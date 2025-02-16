/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 7029048 8217340 8217216
 * @summary Ensure that the launcher defends against user settings of the
 *          LD_LIBRARY_PATH environment variable on Unixes
 * @requires os.family != "windows" & os.family != "mac" & !vm.musl & os.family != "aix"
 * @library /test/lib
 * @compile -XDignore.symbol.file ExecutionEnvironment.java Test7029048.java
 * @run main/othervm -DexpandedLdLibraryPath=false Test7029048
 */

/**
 * @test
 * @bug 7029048 8217340 8217216
 * @summary Ensure that the launcher defends against user settings of the
 *          LD_LIBRARY_PATH environment variable on Unixes
 * @requires os.family == "aix" | vm.musl
 * @library /test/lib
 * @compile -XDignore.symbol.file ExecutionEnvironment.java Test7029048.java
 * @run main/othervm -DexpandedLdLibraryPath=true Test7029048
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test7029048 extends TestHelper {

    static int passes = 0;
    static int errors = 0;

    private static final String LIBJVM = ExecutionEnvironment.LIBJVM;
    private static final String LD_LIBRARY_PATH =
            ExecutionEnvironment.LD_LIBRARY_PATH;
    private static final String LD_LIBRARY_PATH_64 =
            ExecutionEnvironment.LD_LIBRARY_PATH_64;

    private static final File libDir =
            new File(System.getProperty("sun.boot.library.path"));
    private static final File srcServerDir = new File(libDir, "server");
    private static final File srcLibjvmSo = new File(srcServerDir, LIBJVM);

    private static final File dstLibDir = new File("lib");
    private static final File dstServerDir = new File(dstLibDir, "server");
    private static final File dstServerLibjvm = new File(dstServerDir, LIBJVM);

    private static final File dstClientDir = new File(dstLibDir, "client");
    private static final File dstClientLibjvm = new File(dstClientDir, LIBJVM);

    static final boolean IS_EXPANDED_LD_LIBRARY_PATH =
        Boolean.getBoolean("expandedLdLibraryPath");

    private static final Map<String, String> env = new HashMap<>();

    static String getValue(String name, List<String> in) {
        for (String x : in) {
            String[] s = x.split("=");
            if (name.equals(s[0].trim())) {
                return s[1].trim();
            }
        }
        return null;
    }

    static void run(Map<String, String> env,
            int nLLPComponents, String caseID) {
        env.put(ExecutionEnvironment.JLDEBUG_KEY, "true");
        List<String> cmdsList = new ArrayList<>();
        cmdsList.add(javaCmd);
        cmdsList.add("-server");
        cmdsList.add("-jar");
        cmdsList.add(ExecutionEnvironment.testJarFile.getAbsolutePath());
        String[] cmds = new String[cmdsList.size()];
        TestResult tr = doExec(env, cmdsList.toArray(cmds));
        System.out.println(tr);
        analyze(tr, nLLPComponents, caseID);
    }

    static void analyze(TestResult tr, int nLLPComponents, String caseID) {
        String envValue = getValue(LD_LIBRARY_PATH, tr.testOutput);
       /*
        * the envValue can never be null, since the test code should always
        * print a "null" string.
        */
        if (envValue == null) {
            throw new RuntimeException("NPE, likely a program crash ??");
        }
        int len = (envValue.equals("null")
                   ? 0 : envValue.split(File.pathSeparator).length);
        if (len == nLLPComponents) {
            System.out.println(caseID + ": OK");
            passes++;
        } else {
            System.out.println("FAIL: test7029048, " + caseID);
            System.out.println(" expected " + nLLPComponents
                               + " but got " + len);
            System.out.println(envValue);
            errors++;
        }
    }

    /*
     * Describe the cases that we test.  Each case sets the environment
     * variable LD_LIBRARY_PATH to a different value.  The value associated
     * with a case is the number of path elements that we expect the launcher
     * to add to that variable.
     */
    private static enum TestCase {
        NO_DIR(0),                      // Directory does not exist
        NO_LIBJVM(0),                   // Directory exists, but no libjvm.so
        LIBJVM(3);                      // Directory exists, with a libjvm.so
        private final int value;
        TestCase(int i) {
            this.value = i;
        }
    }

    /*
     * test for 7029048
     */
    static void test7029048() throws IOException {
        String desc = null;
        for (TestCase v : TestCase.values()) {
            switch (v) {
                case LIBJVM:
                    // copy the files into the directory structures
                    copyFile(srcLibjvmSo, dstServerLibjvm);
                    // does not matter if it is client or a server
                    copyFile(srcLibjvmSo, dstClientLibjvm);
                    desc = "LD_LIBRARY_PATH should be set";
                    break;
                case NO_LIBJVM:
                    if (!dstClientDir.exists()) {
                        Files.createDirectories(dstClientDir.toPath());
                    } else {
                        Files.deleteIfExists(dstClientLibjvm.toPath());
                    }

                    if (!dstServerDir.exists()) {
                        Files.createDirectories(dstServerDir.toPath());
                    } else {
                        Files.deleteIfExists(dstServerLibjvm.toPath());
                    }

                    desc = "LD_LIBRARY_PATH should not be set (no libjvm.so)";

                    if (IS_EXPANDED_LD_LIBRARY_PATH) {
                        printSkipMessage(desc);
                    }
                    if (TestHelper.isAIX) {
                        System.out.println("Skipping test case \"" + desc +
                                           "\" because the Aix launcher adds the paths in any case.");
                    }
                    if (IS_EXPANDED_LD_LIBRARY_PATH || TestHelper.isAIX) {
                        continue;
                    }
                    break;
                case NO_DIR:
                    if (dstLibDir.exists()) {
                        recursiveDelete(dstLibDir);
                    }

                    desc = "LD_LIBRARY_PATH should not be set (no directory)";

                    if (IS_EXPANDED_LD_LIBRARY_PATH) {
                        printSkipMessage(desc);
                    }
                    if (TestHelper.isAIX) {
                        System.out.println("Skipping test case \"" + desc +
                                           "\" because the Aix launcher adds the paths in any case.");
                    }
                    if (IS_EXPANDED_LD_LIBRARY_PATH || TestHelper.isAIX) {
                        continue;
                    }
                    break;
                default:
                    throw new RuntimeException("unknown case");
            }

            /*
             * Case 1: set the server path
             */
            env.clear();
            env.put(LD_LIBRARY_PATH, dstServerDir.getAbsolutePath());
            run(env,
                v.value + 1,            // Add one to account for our setting
                "Case 1: " + desc);

            /*
             * Case 2: repeat with client path
             */
            env.clear();
            env.put(LD_LIBRARY_PATH, dstClientDir.getAbsolutePath());
            run(env,
                v.value + 1,            // Add one to account for our setting
                "Case 2: " + desc);

            if (isSolaris) {
                /*
                 * Case 3: set the appropriate LLP_XX flag,
                 * java64 LLP_64 is relevant, LLP_32 is ignored
                 */
                env.clear();
                env.put(LD_LIBRARY_PATH_64, dstServerDir.getAbsolutePath());
                run(env,
                    v.value,            // Do not add one, since we didn't set
                                        // LD_LIBRARY_PATH here
                    "Case 3: " + desc);
            }
        }
        return;
    }

    private static void printSkipMessage(String description) {
        System.out.printf("Skipping test case '%s' because the Aix and musl launchers" +
                          " add the paths in any case.%n", description);
    }

    public static void main(String... args) throws Exception {
        if (!TestHelper.haveServerVM) {
            System.out.println("Note: test relies on server vm, not found, exiting");
            return;
        }
        // create our test jar first
        ExecutionEnvironment.createTestJar();

        // run the tests
        test7029048();
        if (errors > 0) {
            throw new Exception("Test7029048: FAIL: with "
                    + errors + " errors and passes " + passes);
        } else if (isSolaris && passes < 9) {
            throw new Exception("Test7029048: FAIL: " +
                    "all tests did not run, expected " + 9 + " got " + passes);
        } else if (isLinux && passes < 6) {
             throw new Exception("Test7029048: FAIL: " +
                    "all tests did not run, expected " + 6 + " got " + passes);
        } else {
            System.out.println("Test7029048: PASS " + passes);
        }
    }

}
