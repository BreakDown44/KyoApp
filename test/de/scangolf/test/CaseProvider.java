package de.scangolf.test;

import java.util.List;

/** Testklassen mit datengetriebenen Fällen (z. B. ein Fall pro Testbild). */
public interface CaseProvider {
    List<TestCase> cases() throws Exception;
}
