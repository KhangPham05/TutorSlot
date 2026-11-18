// "Thu, Nov 19, 1:00 PM to 2:00 PM". The API's times have no time zone, and the browser reads
// such a string as local time, so it shows exactly what the tutor entered.
export function formatSession(startTime: string, endTime: string) {
  const start = new Date(startTime);
  const day = start.toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" });
  return `${day}, ${formatTime(start)} to ${formatTime(new Date(endTime))}`;
}

function formatTime(date: Date) {
  return date.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
}
