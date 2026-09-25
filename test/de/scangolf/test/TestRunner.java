package de.scangolf.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Minimaler Test-Runner ohne Abhängigkeiten.
 *
 * Konvention: Jede Klasse, deren Name auf "Test" endet, wird übergeben. Alle öffentlichen,
 * parameterlosen Instanzmethoden, deren Name mit "test" beginnt, sind Testfälle.
 * Implementiert eine Klasse {@link CaseProvider}, kommen deren datengetriebene Fälle dazu.
 *
 * Filter: -Dtest.filter=Teilstring (auf "Klasse.methode").
 * Exit-Code 1, wenn ein Test fehlschlägt oder keiner gelaufen ist.
 */
public final class TestRunner {

    private int passed;
    private int failed;
    private int skipped;
    private final List<String> failures = new ArrayList<>();
    private final String filter = System.getProperty("test.filter", "");

    public static void main(String[] args) throws Exception {
        TestRunner r = new TestRunner();
        long t0 = System.nanoTime();
        for (String cls : args) {
            r.runClass(Class.forName(cls));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println();
        System.out.println("==================================================");
        System.out.printf("Tests gesamt: %d   bestanden: %d   fehlgeschlagen: %d   übersprungen: %d   (%.1f s)%n",
                r.passed + r.failed + r.skipped, r.passed, r.failed, r.skipped, ms / 1000.0);
        if (!r.failures.isEmpty()) {
            System.out.println("Fehlgeschlagen:");
            for (String f : r.failures) {
                System.out.println("  - " + f);
            }
        }
        System.out.println("==================================================");
        // Immer explizit beenden: nach dem Fenstertest laufen sonst AWT-Threads weiter.
        System.exit(r.failed > 0 || r.passed == 0 ? 1 : 0);
    }

    private void runClass(Class<?> cls) throws Exception {
        Method[] methods = cls.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        List<Method> tests = new ArrayList<>();
        for (Method m : methods) {
            if (m.getName().startsWith("test") && m.getParameterCount() == 0
                    && !Modifier.isStatic(m.getModifiers())) {
                tests.add(m);
            }
        }
        boolean provider = CaseProvider.class.isAssignableFrom(cls);
        if (tests.isEmpty() && !provider) {
            return;
        }
        String simple = cls.getSimpleName();
        Object inst = cls.getDeclaredConstructor().newInstance();
        for (Method m : tests) {
            String name = simple + "." + m.getName();
            if (!name.contains(filter)) {
                continue;
            }
            run(name, () -> {
                try {
                    m.invoke(inst);
                } catch (InvocationTargetException e) {
                    Throwable c = e.getCause();
                    if (c instanceof RuntimeException) {
                        throw (RuntimeException) c;
                    }
                    if (c instanceof Error) {
                        throw (Error) c;
                    }
                    throw new RuntimeException(c);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        if (provider) {
            List<TestCase> cases;
            try {
                cases = ((CaseProvider) inst).cases();
            } catch (Throwable t) {
                record(simple + ".cases()", t, 0);
                return;
            }
            for (TestCase c : cases) {
                String name = simple + "[" + c.name + "]";
                if (name.contains(filter)) {
                    run(name, c.body);
                }
            }
        }
    }

    private void run(String name, Runnable body) {
        long t0 = System.nanoTime();
        try {
            body.run();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            passed++;
            System.out.printf("PASS  %-60s %6d ms%n", name, ms);
        } catch (TestSkipped s) {
            skipped++;
            System.out.printf("SKIP  %-60s (%s)%n", name, s.getMessage());
        } catch (Throwable t) {
            record(name, t, (System.nanoTime() - t0) / 1_000_000);
        }
        System.out.flush();
    }

    private void record(String name, Throwable t, long ms) {
        failed++;
        failures.add(name + ": " + t);
        System.out.printf("FAIL  %-60s %6d ms%n", name, ms);
        System.out.println("      " + t);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(6, st.length); i++) {
            System.out.println("        at " + st[i]);
        }
    }
}
