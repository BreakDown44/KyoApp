package de.scangolf.core.app;

import de.scangolf.core.cert.Certificate;

/**
 * Plattform-Adapter: druckt die Urkunde (A4 hoch). Zum Zeichnen steht
 * {@link de.scangolf.core.cert.CertificateRenderer} über ein Canvas der Plattform bereit.
 */
public interface PrintService {

    interface Callback {
        void printed(String info);

        void failed(String message);
    }

    void print(Certificate certificate, Callback callback);
}
