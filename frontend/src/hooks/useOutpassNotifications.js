import { useEffect, useRef } from 'react';
import toast from 'react-hot-toast';

const LAST_CHECK_KEY = 'outpass_notif_last_check';

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
      if (!o.processedAt) return false;
      const processed = new Date(o.processedAt);
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
