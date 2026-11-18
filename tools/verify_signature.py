#!/usr/bin/env python3
"""Check a spike result: does the agent's signature value verify?

The spike page (tools/spike.html) saves what it sent to the agent and what came
back as a JSON file. This script checks, with the certificate's public key, that
`signatureValue` is a valid signature over `toBeSigned` (SHA-256; PKCS #1 v1.5
for RSA, DER ECDSA for EC), the way Sahihi's server does before it accepts one,
and that `/rest/sign` signed with the certificate `/rest/certificates` chose.

    python3 tools/verify_signature.py spike-result.json

Needs the `cryptography` package (Apache-2.0 or BSD): pip install cryptography.
Exit code 0 means it verified.
"""

import base64
import json
import sys

from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa


def main(path: str) -> int:
    with open(path, encoding="utf-8") as f:
        result = json.load(f)
    to_be_signed = base64.b64decode(result["toBeSigned"])
    chosen = result["certificates"]["response"]
    signed = result["sign"]["response"]

    cert = x509.load_der_x509_certificate(base64.b64decode(signed["certificate"]))
    chosen_der = base64.b64decode(chosen["certificate"])
    print(f"Signer:     {cert.subject.rfc4514_string()}")
    print(f"Issuer:     {cert.issuer.rfc4514_string()}")
    print(f"Serial:     {cert.serial_number:x}")
    print(f"Algorithm:  {signed['signatureAlgorithm']}")
    if cert.public_bytes(serialization.Encoding.DER) != chosen_der:
        print("FAIL: /rest/sign used a different certificate from the one chosen.")
        return 1

    value = base64.b64decode(signed["signatureValue"])
    key = cert.public_key()
    try:
        if isinstance(key, rsa.RSAPublicKey):
            key.verify(value, to_be_signed, padding.PKCS1v15(), hashes.SHA256())
        elif isinstance(key, ec.EllipticCurvePublicKey):
            key.verify(value, to_be_signed, ec.ECDSA(hashes.SHA256()))
        else:
            print(f"FAIL: unsupported key type {type(key).__name__}.")
            return 1
    except InvalidSignature:
        print("FAIL: the signature value does not verify.")
        return 1
    print(f"OK: {len(value)}-byte signature over {len(to_be_signed)} bytes verifies.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
