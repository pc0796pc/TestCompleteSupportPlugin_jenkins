/*
 * The MIT License
 *
 * Copyright (c) 2015, SmartBear Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.smartbear.jenkins.plugins.testcomplete;

import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import javax.crypto.Cipher;
import java.lang.ref.WeakReference;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class Utils {

    private static final String PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCD43scUktBOFoR10dS80DbFJf" +
        "MgJoyNGtfxVyQ6DKwmzb1OS+P3E5Y47K3G6fXX8OfhT0WmQ/Aqr61nUXxRgn2cFH" +
        "Kyc4rjFjfMTkPGkv7rWdIuu+4VR9PYEXar4OyCQEThfhdDSPzfHJ8oiPNqkXe5IY" +
        "L1xQevURO0+Sapzf7wIDAQAB";

    private static final int ENC_CHUNK_MAX_SIZE = 116;

    private Utils() {
    }

    public static boolean isWindows(VirtualChannel channel, BuildListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Boolean call() throws Exception {
                    String os = System.getProperty("os.name");
                    if (os != null) {
                        os = os.toLowerCase();
                    }
                    return (os != null && os.contains("windows"));
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return false;
        }
    }

    public static boolean IsLaunchedAsSystemUser(VirtualChannel channel, final BuildListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Boolean call() throws Exception {

                    // Trying to check whether we are running on System account

                    String winDir = System.getenv("WINDIR");
                    if (winDir == null) {
                        return false;
                    }

                    String userProfile = System.getenv("USERPROFILE");
                    if (userProfile == null) {
                        return false;
                    }

                    return userProfile.startsWith(winDir);
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return false;
        }
    }

    public static long getSystemTime(VirtualChannel channel, BuildListener listener) {
        try {
            return channel.call(new Callable<Long, Exception>() {

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Long call() throws Exception {
                    return System.currentTimeMillis();
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return 0;
        }
    }

    public static long safeConvertDate(String oleDate) {
        double dateToConvert = 0f;
        try {
            dateToConvert = Double.parseDouble(oleDate);
        } catch (NumberFormatException e) {
            // Do nothing
        }
        return OLEDateToMillis(dateToConvert);
    }

    public static long OLEDateToMillis(double dSerialDate)
    {
        return (long) ((dSerialDate - 25569) * 24 * 3600 * 1000);
    }

    public static String encryptPassword(String password) throws Exception {
        byte[] keyRawData = new Base64().decode(PUBLIC_KEY);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new X509EncodedKeySpec(keyRawData);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(ks);
        byte[] encryptedData = encrypt(password, publicKey);

        return new Base64().encode(encryptedData);
    }

    private static byte[] encrypt(String data, Key publicKey) throws Exception {
        ArrayList<byte[]> resultData = new ArrayList<byte[]>();

        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, publicKey);
        byte dataRaw[] = data.getBytes("UTF-16LE");

        int chunksCount = dataRaw.length / ENC_CHUNK_MAX_SIZE +
                ((dataRaw.length % ENC_CHUNK_MAX_SIZE) > 0 ? 1 : 0);
        int remaining = dataRaw.length;

        for (int i = 0; i < chunksCount; i++) {
            int startIndex = i * ENC_CHUNK_MAX_SIZE;
            int length = Math.min(remaining, ENC_CHUNK_MAX_SIZE);
            remaining -= length;
            resultData.add(reverseOrder(rsa.doFinal(dataRaw, startIndex, length)));
        }

        int totalLength = 0;
        for (int i = 0; i < resultData.size(); i++) {
            totalLength += resultData.get(i).length;
        }

        byte[] result = new byte[totalLength];
        int position = 0;
        for (int i = 0; i < resultData.size(); i++) {
            byte[] current = resultData.get(i);
            System.arraycopy(current, 0, result, position, current.length);
            position += current.length;
        }

        return result;
    }

    private static byte[] reverseOrder(byte[] data) {
        int length = data.length;
        byte[] reversedData = new byte[length];
        for (int i = 0; i < length; i++) {
            reversedData[i] = data[length - 1 - i];
        }
        return reversedData;
    }

    public static class BusyNodeList {

        private Map<WeakReference<Node>, Semaphore> nodeLocks = new HashMap<WeakReference<Node>, Semaphore>();

        public void lock(Node node, BuildListener listener) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                    }
                }

                if (semaphore == null) {
                    semaphore = new Semaphore(1, true);
                    nodeLocks.put(new WeakReference<Node>(node), semaphore);
                } else {
                    listener.getLogger().println();
                    TcLog.info(listener, Messages.TcTestBuilder_WaitingForNodeRelease());
                }
            }

            semaphore.acquire();
        }

        public void release(Node node) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                    }
                }
            }
            if (semaphore != null) {
                semaphore.release();
            }

            Thread.sleep(200);

            // cleanup the unused items
            synchronized (this) {
                List<WeakReference<Node>> toRemove = new ArrayList<WeakReference<Node>>();

                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                        if (semaphore.availablePermits() > 0) {
                            toRemove.add(nodeRef);
                        }
                    }
                }

                for (WeakReference<Node> nodeRef : toRemove) {
                    nodeLocks.remove(nodeRef);
                }
            }
        }
    }
}