# Mobile self-pay settlement alignment

## Context

The latest `develop` payment decision supersedes the earlier demo that charged
laboratory orders and prescription items separately. The patient mobile app
must model the hospital journey as one visit with two payment moments:

1. the consultation fee is prepaid while booking (`GENERAL_CONSULTATION`,
   150,000 VND in the current fixture), and
2. the visit is settled once the doctor has issued the prescription (or the
   consultation ends without a prescription).

Laboratory orders enter their own FIFO queue immediately. A confirmed
prescription also enters the pharmacy queue immediately. Neither step creates
an independent payment gate.

## Design

The mobile demo keeps its adapter boundary so it can be replaced by the Visit
Settlement backend later. It calculates:

```text
totalVisitCost = consultation + laboratory orders + medication
amountDue      = max(0, totalVisitCost - prepaidAmount)
refundDue      = max(0, prepaidAmount - totalVisitCost)
```

The final settlement statuses are `PAYMENT_DUE`, `SETTLED`, `REFUND_PENDING`,
and `REFUNDED`. A `PAYMENT_DUE` settlement blocks dispensing; `SETTLED`,
`REFUND_PENDING`, and `REFUNDED` allow dispensing. Refund processing does not
block dispensing.

## Mobile journey

```text
Booking payment
  -> appointment ticket/check-in/clinic queue
  -> consultation
  -> (optional) laboratory queue and result review
  -> prescription / consultation conclusion
  -> final settlement
  -> pharmacy FIFO and dispense
  -> completed visit
```

The demo UI exposes the settlement summary, the outstanding amount (or refund
amount), online mock/cash acknowledgement, and the dispense/completion actions.
The old laboratory-payment and prescription-payment controls remain accepted
only as persistence compatibility for previously stored demo snapshots; they
are not part of the new UI or the new default journey.
