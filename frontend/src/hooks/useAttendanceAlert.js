import { useState, useEffect, useRef } from 'react';
import attendanceService from '../services/attendanceService';

const useAttendanceAlert = () => {
  const [showAlert, setShowAlert] = useState(false);
  const [activeSession, setActiveSession] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => {
    attendanceService.requestNotificationPermission();

    const checkSession = async () => {
      try {
        const res = await attendanceService.getActiveSession();
        setActiveSession(res.data);
      } catch {
        // silently ignore polling errors
      }
    };

    checkSession();

    pollRef.current = setInterval(checkSession, 5000);

    const cleanup = attendanceService.onSessionAlert(() => {
      setShowAlert(true);
      checkSession();
    });

    return () => {
      clearInterval(pollRef.current);
      cleanup();
    };
  }, []);

  const dismissAlert = () => setShowAlert(false);

  return { showAlert, activeSession, dismissAlert };
};

export default useAttendanceAlert;
