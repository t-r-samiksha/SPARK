import { prisma } from '../db/client';
import { bankRootCaPublicKey } from '../config';
import { canonicalizeForSigning, ed25519Verify, pemDecode } from '../crypto';
import { Certificate } from '../types';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

export type CertificateCheck =
  | { valid: true; cert: Certificate }
  | { valid: false; reason: string };

/**
 * Verifies an embedded transaction-party certificate is trustworthy: validly signed by the Bank
 * Root CA, within its validity window, not revoked, and bound to the device_id the caller expects
 * (a transaction's payer.device_id must match payer.cert.device_id — otherwise a device could
 * attach any valid cert to spoof a different identity).
 *
 * Deliberately does NOT check the Device DB table for a matching row — per the offline-verification
 * design (docs/transaction-format.md), a validly Bank-signed, non-revoked, non-expired cert *is*
 * the proof of enrollment. This mirrors what a payee device does fully offline at receipt time,
 * using only the embedded cert + its cached CRL, not a database.
 */
export async function verifyPayerCertificate(certPem: string, expectedDeviceId: string): Promise<CertificateCheck> {
  let certJson: unknown;
  try {
    certJson = JSON.parse(pemDecode(CERT_PEM_LABEL, certPem).toString('utf8'));
  } catch {
    return { valid: false, reason: 'payer.cert is not a valid SPARK DEVICE CERTIFICATE PEM envelope' };
  }

  const cert = certJson as Certificate;
  if (
    typeof cert.device_id !== 'string' ||
    typeof cert.account_id !== 'string' ||
    typeof cert.device_public_key !== 'string' ||
    typeof cert.serial_number !== 'string' ||
    typeof cert.not_before !== 'string' ||
    typeof cert.not_after !== 'string' ||
    typeof cert.signature !== 'string'
  ) {
    return { valid: false, reason: 'payer.cert is missing required fields' };
  }

  if (cert.device_id !== expectedDeviceId) {
    return { valid: false, reason: 'payer.cert.device_id does not match payer.device_id' };
  }

  const signingBytes = canonicalizeForSigning({ ...cert });
  if (!ed25519Verify(bankRootCaPublicKey(), signingBytes, cert.signature)) {
    return { valid: false, reason: 'payer.cert signature is not valid for the Bank Root CA key' };
  }

  const now = new Date();
  if (now < new Date(cert.not_before) || now > new Date(cert.not_after)) {
    return { valid: false, reason: 'payer.cert is outside its validity window' };
  }

  const revoked = await prisma.revokedCertificate.findUnique({ where: { certSerial: cert.serial_number } });
  if (revoked) {
    return { valid: false, reason: 'payer.cert has been revoked' };
  }

  return { valid: true, cert };
}
