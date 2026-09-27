# ADR-0006: GDPR retention and automated decisions

Aggregates store opaque subject tokens only. Contract servicing uses Article 6(1)(b); statutory reporting and retention use the applicable legal obligation. A jurisdiction profile sets `retention_until` (the demo default is seven years after closure). Erasure pseudonymizes unnecessary linkage while retaining immutable financial facts under legal hold. Exports and restricted erasure ports support Articles 15/20 and 17.

LTV warnings and liquidation are automated actions with financial effect. The system records reason codes and price/debt evidence, supports an analyst pause and dispute/override workflow, and uses compensating ledger entries for completed actions. Production activation requires a DPIA and jurisdictional legal review; this portfolio implementation does not assert regulatory certification.
