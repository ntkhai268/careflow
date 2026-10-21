"""Hospital catalog in, evidence-preserving proposal out. No prescription writes."""
import hashlib
import re
import unicodedata
from datetime import date

from .catalog import Catalog
from .extraction import RulesExtractor
from .text import fold, number, NUMBER_PATTERN

SCHEMA = "careflow.voice-prescription.v1"
FIELDS = ("spoken_name", "strength", "dosage_form", "quantity", "quantity_unit", "dose",
          "frequency", "route", "duration", "timing", "min_interval", "max_daily_dose")


def camel(value):
    head,*tail=value.split("_")
    return head+"".join(part.title() for part in tail)


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def exact_integer(value):
    if value is None or isinstance(value,bool):return None
    try:
        parsed=float(value)
        return int(parsed) if parsed>0 and parsed.is_integer() and parsed<=2147483647 else None
    except (ValueError,OverflowError):return None


def duration_days(value):
    if not value:return None
    m=re.fullmatch(rf"(?:dung\s+)?(?:trong\s+)?({NUMBER_PATTERN})\s+ngay(?:\s+nhe)?",fold(value).strip())
    return exact_integer(number(m[1])) if m else None


def infer(transcript, medicines, catalog_version, revision=1):
    transcript=unicodedata.normalize("NFC",transcript.strip())
    if not 1<=len(transcript)<=12000 or not isinstance(revision,int) or revision<1:
        raise ValueError("INVALID_TRANSCRIPT")
    if not isinstance(medicines,list) or not 1<=len(medicines)<=10000 or not catalog_version:
        raise ValueError("INVALID_CATALOG")
    entries=[];codes=set()
    for m in medicines:
        if not isinstance(m,dict) or not isinstance(m.get("code"),str) or not isinstance(m.get("name"),str):
            raise ValueError("INVALID_CATALOG")
        if not m['code'].strip() or not m['name'].strip() or len(m['name'])>200 or m['code'] in codes:
            raise ValueError("INVALID_CATALOG")
        codes.add(m['code'])
        if m.get('active') is not True:continue
        # Never extract ingredient/strength/form from free-text descriptions.
        entries.append(dict(id=m['code'],name=m['name'],ingredient="",strength="",dosage_form="",
                            unit=m.get('unit'),status='active',source='prescription-catalog',
                            checked_at=date.today().isoformat()))
    if not entries:raise ValueError("EMPTY_ACTIVE_CATALOG")
    result=RulesExtractor(Catalog(entries)).extract(transcript)
    items=[];draft=[];blockers=[]
    for item in result.items:
        row={camel(k):getattr(item,k) for k in FIELDS}
        row.update(normalizedName=item.normalized_name,matchStatus=item.match_status,
                   medicineCode=None,candidates=[dict(medicineCode=c['id'],name=c['name'],unit=c.get('unit')) for c in item.candidates],
                   issues=item.issues,fieldConflicts={camel(k):[dict(text=e.text,start=e.start,end=e.end) for e in values] for k,values in item.field_conflicts.items()},
                   missingFields=[camel(k) for k in FIELDS if k!='spoken_name' and getattr(item,k) is None])
        row['evidence']={camel(k):dict(text=v.text,start=v.start,end=v.end,transcriptRevision=revision,
                              transcriptHash=sha(transcript),offsetUnit='unicode_codepoint') for k,v in item.evidence.items()}
        for evidence in row['evidence'].values():
            if transcript[evidence['start']:evidence['end']]!=evidence['text']:
                raise ValueError('INVALID_EVIDENCE')
        exact=[e for e in entries if unicodedata.normalize('NFC',e['name']).casefold()==unicodedata.normalize('NFC',item.spoken_name).casefold()]
        if len(exact)==1 and item.match_status=='matched' and not item.field_conflicts:
            chosen=exact[0];row['medicineCode']=chosen['id']
            qty=exact_integer(item.quantity);days=duration_days(item.duration)
            reasons=[]
            if item.quantity is not None and qty is None:reasons.append('QUANTITY_REQUIRES_INTEGER')
            if item.quantity_unit and chosen.get('unit') and fold(item.quantity_unit).strip()!=fold(chosen['unit']).strip():
                reasons.append('QUANTITY_UNIT_CONFLICT');qty=None
            if item.duration is not None and days is None:reasons.append('DURATION_REQUIRES_EXPLICIT_INTEGER_DAYS')
            unsupported={camel(k):getattr(item,k) for k in ('strength','dosage_form','route','min_interval','max_daily_dose') if getattr(item,k) is not None}
            proposal=dict(medicineCode=chosen['id'],medicineName=chosen['name'],unit=chosen.get('unit'),
                          dosage=item.dose,frequency=item.frequency,timing=item.timing,duration=days,
                          quantity=qty,notes=None,unmappedFields=unsupported,sourceItemIndex=len(items))
            draft.append(proposal)
            if unsupported:reasons.append('UNMAPPED_FIELDS_REQUIRE_EDITOR_REVIEW')
            blockers.extend(dict(itemIndex=len(items),code=reason) for reason in reasons)
        else:
            blockers.append(dict(itemIndex=len(items),code='CATALOG_SELECTION_REQUIRED'))
        items.append(row)
    if not items:blockers.append(dict(itemIndex=None,code='NO_ACTIVE_MEDICATION'))
    return dict(schemaVersion=SCHEMA,transcript=transcript,transcriptRevision=revision,
                transcriptHash=sha(transcript),catalogVersion=catalog_version,items=items,
                warnings=result.warnings,modelInfo=dict(extractor='rules-v3',clinicalValidation=False),
                prescriptionDraft=dict(items=draft,blockers=blockers,canSaveWithoutCorrection=bool(draft) and not blockers),
                processingMs=result.elapsed_ms)
