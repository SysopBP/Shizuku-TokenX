package rikka.shizuku.server;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.ServiceManager;
import android.system.Os;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParser;
import org.json.JSONArray;
import org.json.JSONObject;

/** Experimental, fixed-package shared-UID enrollment. Invoked only by the manager endpoint. */
final class TokenXUidTestSigning {
    static final String PACKAGE = "moe.shizuku.tokenx.uidtest";
    private static final File DIRECTORY = new File("/data/system/tokenx_uid_test");
    private static final File RECEIPT = new File(DIRECTORY, "signing-recovery.json");
    private static final int SHARED_USER_ID = 2;

    static Bundle run(Context context, int action, byte[] certificate) throws Exception {
        if (Build.VERSION.SDK_INT < 36 || Process.myUid() != 1000)
            throw new IllegalStateException("Shared UID test requires Android 16+ System Server");
        if (certificate == null || certificate.length < 128 || certificate.length > 16384)
            throw new IllegalArgumentException("Invalid test certificate");
        Signature target = new Signature(certificate);
        Object binder = ServiceManager.getService("package");
        Object service = null;
        for (Class<?> type = binder.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.android.server.pm.PackageManagerService")) { service = binder; break; }
            for (Field candidate : type.getDeclaredFields()) {
                if (candidate.getType().getName().equals("com.android.server.pm.PackageManagerService")) {
                    candidate.setAccessible(true); service = candidate.get(binder); break;
                }
            }
            if (service != null) break;
        }
        if (service == null) throw new IllegalStateException("Local Package Manager contract unavailable");
        Object lock = field(service.getClass(), "mLock").get(service);
        Object settings = field(service.getClass(), "mSettings").get(service);
        Method sharedLookup = method(settings.getClass(), "getSharedUserLPw",
                String.class, int.class, int.class, boolean.class);
        Method write = method(service.getClass(), "writeSettings", boolean.class);
        synchronized (lock) {
            // create=false: never invent or replace the platform's shared user.
            Object shared = sharedLookup.invoke(settings, "android.uid.system", 0, 0, false);
            if (shared == null) throw new IllegalStateException("System shared user missing");
            Object signatures = field(shared.getClass(), "signatures").get(shared);
            Field detailsField = field(signatures.getClass(), "mSigningDetails");
            Object details = detailsField.get(signatures);
            Field pastField = field(details.getClass(), "mPastSigningCertificates");
            Method currentMethod = method(details.getClass(), "getSignatures");
            Method flagsGet = method(Signature.class, "getFlags");
            Method flagsSet = method(Signature.class, "setFlags", int.class);
            Method capability = method(details.getClass(), "hasCertificate", Signature.class, int.class);
            Method changed = method(shared.getClass(), "onChanged");
            Signature[] current = (Signature[]) currentMethod.invoke(details);
            Signature[] before = (Signature[]) pastField.get(details);
            if (current == null || current.length != 1) throw new IllegalStateException("Unsupported platform signing layout");
            if (before != null && (before.length == 0 || before.length > 32 || !before[before.length - 1].equals(current[0])))
                throw new IllegalStateException("Unsupported existing platform signing lineage");
            validateDirectory();
            JSONObject receipt = RECEIPT.exists() ? new JSONObject(new String(
                    new AtomicFile(RECEIPT).readFully(), StandardCharsets.UTF_8)) : null;
            if (receipt != null && (receipt.getInt("version") != 1 || !receipt.getString("certificate").equals(encoded(certificate))))
                throw new IllegalStateException("Another test build has a recovery record; remove it using that build first");
            if (receipt == null) verifyDisk(policy(encode(before, flagsGet)));
            else {
                // A failed persistence call may leave memory and disk on different
                // sides of the recorded transaction. Only those two known states
                // are recoverable; an independent third state is rejected.
                try { verifyDisk(policy(receipt.getJSONArray("before"))); }
                catch (Exception mismatch) { verifyDisk(policy(receipt.getJSONArray("after"))); }
            }
            boolean authorized = (Boolean) capability.invoke(details, target, SHARED_USER_ID);
            Bundle result = new Bundle();
            result.putBoolean("authorized", authorized);
            result.putBoolean("receipt", receipt != null);
            result.putString("detail", "Package Manager contract and platform lineage verified");
            if (action == 20) return result; // read-only compatibility preflight

            if (action == 21) {
                if (receipt != null) {
                    if (!authorized) throw new IllegalStateException("Recovery pending; remove the test first");
                    return result;
                }
                PackageInfo installed = context.getPackageManager().getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
                if (installed.applicationInfo.uid == 1000 || installed.signingInfo == null ||
                        installed.signingInfo.getApkContentsSigners().length != 1 ||
                        !installed.signingInfo.getApkContentsSigners()[0].equals(target))
                    throw new SecurityException("Matching ordinary test app must be installed first");
                if (authorized) throw new IllegalStateException("Test certificate is already trusted without our recovery record");
                // Preserve every platform certificate and flag, including the current tail.
                List<UidTestLineagePolicy.Certificate> enrolled = UidTestLineagePolicy.enroll(
                        new UidTestLineagePolicy.Certificate(encoded(current[0].toByteArray()), (Integer) flagsGet.invoke(current[0])),
                        policy(encode(before, flagsGet)), encoded(certificate));
                Signature[] after = new Signature[enrolled.size()];
                for (int i = 0; i < after.length; i++) {
                    after[i] = new Signature(Base64.decode(enrolled.get(i).value(), Base64.NO_WRAP));
                    flagsSet.invoke(after[i], enrolled.get(i).flags());
                }
                Object replacement = details.getClass().getConstructor(details.getClass()).newInstance(details);
                pastField.set(replacement, after);
                if (!(Boolean) capability.invoke(replacement, target, SHARED_USER_ID))
                    throw new IllegalStateException("Platform rejected the proposed capability; no changes applied");
                JSONObject journal = new JSONObject().put("version", 1).put("certificate", encoded(certificate))
                        .put("platform", encoded(current[0].toByteArray()))
                        .put("before", encode(before, flagsGet)).put("after", encode(after, flagsGet));
                persist(journal); // durable recovery BEFORE the first signing-state modification
                try {
                    detailsField.set(signatures, replacement);
                    changed.invoke(shared);
                    write.invoke(service, true);
                    verifyDisk(policy(encode(after, flagsGet)));
                    if (!(Boolean) capability.invoke(detailsField.get(signatures), target, SHARED_USER_ID))
                        throw new IllegalStateException("Signing capability verification failed");
                } catch (Exception failure) {
                    detailsField.set(signatures, details);
                    changed.invoke(shared);
                    write.invoke(service, true);
                    verifyDisk(policy(encode(before, flagsGet)));
                    new AtomicFile(RECEIPT).delete();
                    throw failure;
                }
                result.putBoolean("authorized", true); result.putBoolean("receipt", true);
                result.putString("detail", "Test certificate enrolled; recovery record saved");
                return result;
            }

            if (action != 22) throw new IllegalArgumentException("Unsupported signing action");
            if (receipt == null) {
                if (authorized) throw new IllegalStateException("Cannot remove trust without the original recovery record");
                result.putString("detail", "No signing change to restore"); return result;
            }
            String[] packages = context.getPackageManager().getPackagesForUid(1000);
            if (packages == null) throw new IllegalStateException("Cannot enumerate system shared UID");
            for (String name : packages) {
                PackageInfo info = context.getPackageManager().getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) throw new IllegalStateException("Cannot verify installed system package " + name);
                for (Signature signer : info.signingInfo.getApkContentsSigners())
                    if (signer.equals(target)) throw new IllegalStateException("Uninstall all apps using this test certificate first: " + name);
            }
            JSONArray now = encode(before, flagsGet);
            JSONArray savedBefore = receipt.getJSONArray("before"), savedAfter = receipt.getJSONArray("after");
            if (!UidTestLineagePolicy.restore(encoded(current[0].toByteArray()), receipt.getString("platform"),
                    policy(savedBefore), policy(savedAfter), policy(now), List.of())) {
                write.invoke(service, true);
                verifyDisk(policy(savedBefore));
                new AtomicFile(RECEIPT).delete(); result.putBoolean("receipt", false); return result;
            }
            Object restored = details.getClass().getConstructor(details.getClass()).newInstance(details);
            pastField.set(restored, decode(savedBefore, flagsSet));
            detailsField.set(signatures, restored);
            changed.invoke(shared);
            write.invoke(service, true);
            verifyDisk(policy(savedBefore));
            if ((Boolean) capability.invoke(detailsField.get(signatures), target, SHARED_USER_ID))
                throw new IllegalStateException("Trust restoration check failed; recovery record retained");
            new AtomicFile(RECEIPT).delete();
            result.putBoolean("authorized", false); result.putBoolean("receipt", false);
            result.putString("detail", "Original platform lineage restored"); return result;
        }
    }

    private static JSONArray encode(Signature[] signatures, Method flags) throws Exception {
        JSONArray array = new JSONArray();
        if (signatures == null) { array.put(JSONObject.NULL); return array; }
        for (Signature signature : signatures) array.put(new JSONObject()
                .put("certificate", encoded(signature.toByteArray())).put("flags", flags.invoke(signature)));
        return array;
    }
    private static List<UidTestLineagePolicy.Certificate> policy(JSONArray array) throws Exception {
        if (array.length() == 1 && array.isNull(0)) return null;
        List<UidTestLineagePolicy.Certificate> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            result.add(new UidTestLineagePolicy.Certificate(entry.getString("certificate"), entry.getInt("flags")));
        }
        return result;
    }
    private static Signature[] decode(JSONArray array, Method flags) throws Exception {
        if (array.length() == 1 && array.isNull(0)) return null;
        Signature[] signatures = new Signature[array.length()];
        for (int i = 0; i < signatures.length; i++) {
            JSONObject entry = array.getJSONObject(i);
            signatures[i] = new Signature(Base64.decode(entry.getString("certificate"), Base64.NO_WRAP));
            flags.invoke(signatures[i], entry.getInt("flags"));
        }
        return signatures;
    }
    private static String encoded(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
    /** Read back Android's actual XML/ABX settings, including globally indexed certificates. */
    private static void verifyDisk(List<UidTestLineagePolicy.Certificate> expected) throws Exception {
        File settings = new File("/data/system/packages.xml");
        if (settings.length() <= 0 || settings.length() > 32 * 1024 * 1024)
            throw new IllegalStateException("Package settings size unsupported");
        HashMap<String, String> keys = new HashMap<>();
        List<String> indices = new ArrayList<>(); List<Integer> flags = new ArrayList<>();
        boolean found = false, hasPast = false;
        int sharedDepth = -1, pastDepth = -1;
        try (InputStream stream = new FileInputStream(settings)) {
            // Platform resolver supports both text XML and Android Binary XML (ABX).
            XmlPullParser parser = (XmlPullParser) Class.forName("android.util.Xml")
                    .getDeclaredMethod("resolvePullParser", InputStream.class).invoke(null, stream);
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if (name.equals("shared-user") && "android.uid.system".equals(parser.getAttributeValue(null, "name"))) {
                        if (found) throw new IllegalStateException("Duplicate system shared-user settings");
                        sharedDepth = parser.getDepth(); found = true;
                    }
                    if (sharedDepth != -1 && name.equals("pastSigs")) { pastDepth = parser.getDepth(); hasPast = true; }
                    if (name.equals("cert")) {
                        String index = parser.getAttributeValue(null, "index"), key = parser.getAttributeValue(null, "key");
                        if (key != null) keys.put(index, encoded(new Signature(key).toByteArray()));
                        if (pastDepth != -1) {
                            indices.add(index); flags.add(Integer.parseInt(parser.getAttributeValue(null, "flags")));
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (parser.getDepth() == pastDepth) pastDepth = -1;
                    if (parser.getDepth() == sharedDepth) sharedDepth = -1;
                }
            }
        }
        if (!found) throw new IllegalStateException("System shared-user disk settings missing");
        List<UidTestLineagePolicy.Certificate> actual = hasPast ? new ArrayList<>() : null;
        for (int i = 0; i < indices.size(); i++) {
            String key = keys.get(indices.get(i));
            if (key == null) throw new IllegalStateException("Unresolved persisted certificate");
            actual.add(new UidTestLineagePolicy.Certificate(key, flags.get(i)));
        }
        if (!Objects.equals(actual, expected)) throw new IllegalStateException("Persisted signing lineage readback mismatch; recovery record retained");
    }
    private static void validateDirectory() throws Exception {
        if (Files.isSymbolicLink(DIRECTORY.toPath()) || Files.isSymbolicLink(RECEIPT.toPath()))
            throw new SecurityException("Symlink recovery path rejected");
        if (DIRECTORY.exists() && (Os.stat(DIRECTORY.getPath()).st_uid != 1000 ||
                (Os.stat(DIRECTORY.getPath()).st_mode & 077) != 0))
            throw new SecurityException("Recovery directory ownership/mode rejected");
        if (RECEIPT.exists() && Os.stat(RECEIPT.getPath()).st_uid != 1000)
            throw new SecurityException("Recovery record ownership rejected");
    }
    private static void persist(JSONObject record) throws Exception {
        if (!DIRECTORY.exists()) {
            if (!DIRECTORY.mkdir()) throw new IllegalStateException("Cannot create recovery directory");
            Os.chmod(DIRECTORY.getPath(), 0700);
        }
        validateDirectory();
        AtomicFile atomic = new AtomicFile(RECEIPT);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(record.toString().getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(stream); Os.chmod(RECEIPT.getPath(), 0600);
            String actual = new String(atomic.readFully(), StandardCharsets.UTF_8);
            if (!actual.equals(record.toString())) throw new IllegalStateException("Recovery record readback failed");
        } catch (Exception failure) { atomic.failWrite(stream); throw failure; }
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field value = c.getDeclaredField(name); value.setAccessible(true); return value; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method value = c.getDeclaredMethod(name, args); value.setAccessible(true); return value; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
