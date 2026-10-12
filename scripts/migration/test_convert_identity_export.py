"""Synthetic conversion contract; no live source or invented customer mappings."""
import unittest
from convert_identity_export import convert


class IdentityExportTest(unittest.TestCase):
    def fixture(self):
        self.customer = "cus_" + "A" * 22
        self.challenge = "otp_" + "b" * 22
        source = {"challenges": [{"id": "old-challenge", "user_id": "old-customer", "attempts": 0, "max_attempts": 5,
                                  "created_at": "2026-01-01T03:30:00.123456789+03:30", "expires_at": "2026-01-01T00:35:00Z"}],
                  "audit": [{"id": "00112233445566778899aabbccddeeff", "challenge_id": "old-challenge", "user_id": "old-customer", "occurred_at": "2026-01-01T00:00:00Z"}]}
        mapping = {"approved_by": "synthetic operator", "approval_reference": "fixture approval",
                   "customers": {"old-customer": self.customer}, "challenges": {"old-challenge": self.challenge}}
        return source, mapping

    def test_approved_mapping_preserves_uuid_byte_order_and_truncates_microseconds(self):
        source, mapping = self.fixture()
        result = convert(source, mapping, b"synthetic approved mapping bytes")
        self.assertEqual(self.challenge, result["challenges"][0]["id"])
        self.assertEqual("2026-01-01T00:00:00.123456+00:00", result["challenges"][0]["created_at"])
        self.assertEqual("00112233-4455-6677-8899-aabbccddeeff", result["audit"][0]["id"])
        self.assertEqual(1, result["manifest"]["audit_count"])

    def test_current_case_sensitive_ids_pass_through_and_missing_mapping_is_refused(self):
        source, mapping = self.fixture()
        source["challenges"][0]["id"] = self.challenge
        result = convert(source, mapping, b"mapping")
        self.assertEqual(self.challenge, result["challenges"][0]["id"])
        mapping["customers"] = {}
        with self.assertRaises(ValueError):
            convert(source, mapping, b"mapping")

    def test_orphans_duplicates_unapproved_mapping_and_offsetless_time_are_refused(self):
        for defect in ("orphan", "duplicate", "approval", "offset", "integer"):
            source, mapping = self.fixture()
            if defect == "orphan":
                source["audit"][0]["challenge_id"] = "otp_" + "Q" * 22
            elif defect == "duplicate":
                source["challenges"].append(dict(source["challenges"][0]))
            elif defect == "approval":
                mapping.pop("approval_reference")
            elif defect == "offset":
                source["challenges"][0]["created_at"] = "2026-01-01T00:00:00"
            else:
                source["challenges"][0]["attempts"] = "0"
            with self.assertRaises(ValueError):
                convert(source, mapping, b"mapping")


if __name__ == "__main__":
    unittest.main()
