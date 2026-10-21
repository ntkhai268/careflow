export function patientAge(dateOfBirth?: string | null, now = new Date()): number | null {
  if (!dateOfBirth || !/^\d{4}-\d{2}-\d{2}$/.test(dateOfBirth)) return null;
  const [year,month,day] = dateOfBirth.split("-").map(Number);
  const date = new Date(Date.UTC(year,month-1,day));
  if (year<1900 || date.getUTCFullYear()!==year || date.getUTCMonth()!==month-1 || date.getUTCDate()!==day) return null;
  const parts = new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(now);
  const value = (type:string) => Number(parts.find(part=>part.type===type)?.value);
  const currentYear=value("year"),currentMonth=value("month"),currentDay=value("day");
  const age=currentYear-year-(currentMonth<month || (currentMonth===month && currentDay<day)?1:0);
  return age<0 || dateOfBirth>`${currentYear}-${String(currentMonth).padStart(2,"0")}-${String(currentDay).padStart(2,"0")}` ? null : age;
}

export function visitTime(timestamp?: string | null): string {
  if(!timestamp)return "—";
  const date=new Date(timestamp);
  if(Number.isNaN(date.getTime()))return "—";
  return new Intl.DateTimeFormat("vi-VN",{timeZone:"Asia/Ho_Chi_Minh",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).format(date);
}
