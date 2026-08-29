import { useEffect, useRef } from 'react';
import toast from 'react-hot-toast';

const LAST_CHECK_KEY = 'outpass_notif_last_check';

// Backend emits naive LocalDateTime strings (no 'Z'/offset) representing Asia/Kolkata
// wall-clock time. new Date() would otherwise parse these using the browser's local
// timezone, which only happens to be correct when the browser is set to IST. Appending
// the explicit IST offset makes the resulting Date a correct absolute instant regardless
// of the browser's timezone, so it can be safely compared against lastCheckTime (which is
// already an absolute instant, stored via toISOString()).
const parseBackendTimestamp = (value) => {
  if (!value) return null;
  const hasOffset = /[Zz]|[+-]\d{2}:\d{2}$/.test(value);
  return new Date(hasOffset ? value : `${value}+05:30`);
};

const useOutpassNotifications = (outpasses) => {
  const checked = useRef(false);

  useEffect(() => {
    if (!outpasses || outpasses.length === 0 || checked.current) return;
    checked.current = true;

    const lastCheck = localStorage.getItem(LAST_CHECK_KEY);
    const lastCheckTime = lastCheck ? new Date(lastCheck) : null;
    const now = new Date();

    localStorage.setItem(LAST_CHECK_KEY, now.toISOString());

    if (!lastCheckTime) return;

    const recent = outpasses.filter(o => {
      const processed = parseBackendTimestamp(o.processedAt);
      if (!processed) return false;
      return processed > lastCheckTime && (o.status === 'APPROVED' || o.status === 'DECLINED');
    });

    recent.forEach(o => {
      if (o.status === 'APPROVED') {
        toast.success(`Outpass #${o.id} to ${o.placeOfVisit} has been approved!`, { duration: 5000 });
      } else if (o.status === 'DECLINED') {
        toast.error(`Outpass #${o.id} to ${o.placeOfVisit} was declined${o.declineReason ? ': ' + o.declineReason : ''}`, { duration: 6000 });
      }
    });
  }, [outpasses]);
};

export default useOutpassNotifications;
