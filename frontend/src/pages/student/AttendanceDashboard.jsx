import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import attendanceService from '../../services/attendanceService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCalendarCheck, faArrowLeft, faWifi, faMapMarkerAlt, faFingerprint,
  faCheckCircle, faTimesCircle, faSpinner, faPercentage, faCalendarAlt,
  faHistory, faBell,
} from '@fortawesome/free-solid-svg-icons';

const AttendanceDashboard = () => {
  const [todayStatus, setTodayStatus] = useState(null);
  const [activeSession, setActiveSession] = useState(null);
  const [stats, setStats] = useState({ total: 0, present: 0, absent: 0, percentage: 0 });
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [markingStep, setMarkingStep] = useState(null);
  const [stepMessage, setStepMessage] = useState('');
  const [sessionJustStarted, setSessionJustStarted] = useState(false);
  const pollRef = useRef(null);
  const sessionRef = useRef(null);

  useEffect(() => {
    sessionRef.current = activeSession;
  }, [activeSession]);

  useEffect(() => {
    fetchData();
    attendanceService.requestNotificationPermission();

    pollRef.current = setInterval(async () => {
      try {
        const res = await attendanceService.getActiveSession();
        const prevSession = sessionRef.current;
        if (res.data && !prevSession) {
          setActiveSession(res.data);
          setSessionJustStarted(true);
          toast('Attendance is now open! Mark your presence.', { icon: '\u{1F514}' });
        } else if (!res.data && prevSession) {
          setActiveSession(null);
          setSessionJustStarted(false);
        }
      } catch (e) {
        // ignore polling errors
      }
    }, 5000);

    const cleanup = attendanceService.onSessionAlert(() => {
      setSessionJustStarted(true);
      fetchData();
      toast('Attendance session started by warden!', { icon: '\u{1F514}' });
    });

    return () => {
      clearInterval(pollRef.current);
      cleanup();
    };
  }, []);

  const fetchData = async () => {
    try {
      const [todayRes, statsRes, historyRes, sessionRes] = await Promise.all([
        attendanceService.getTodayStatus(),
        attendanceService.getStats(),
        attendanceService.getAttendanceHistory(),
        attendanceService.getActiveSession(),
      ]);
      setTodayStatus(todayRes.data);
      setStats(statsRes.data);
      setHistory(historyRes.data.slice(0, 10));
      setActiveSession(sessionRes.data);
    } catch (error) {
      console.error('Error fetching attendance data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleMarkAttendance = async () => {
    if (todayStatus) {
      toast.error('Attendance already marked for today');
      return;
    }

    // Step 1: Check WiFi
    setMarkingStep('wifi');
    setStepMessage('Checking hostel WiFi connection...');

    try {
      const wifiResult = await attendanceService.checkWifiConnection();
      if (wifiResult.connected) {
        setStepMessage('Connected to hostel WiFi! Marking attendance...');
        await attendanceService.markAttendance('WIFI');
        setMarkingStep('done');
        setStepMessage('Attendance marked via WiFi');
        toast.success('Attendance marked successfully!');
        fetchData();
        return;
      }
    } catch (error) {
      // WiFi check failed, continue to location
    }

    // Step 2: Check Geolocation
    setMarkingStep('location');
    setStepMessage('WiFi not detected. Checking your location...');

    try {
      const locationResult = await attendanceService.checkGeolocation();
      if (!locationResult.withinRange) {
        setMarkingStep(null);
        setStepMessage('');
        toast.error(`You are ${locationResult.distance}m away from hostel. Please be within 50m.`);
        return;
      }

      setStepMessage(`Location verified (${locationResult.distance}m from hostel). Verifying identity...`);

      // Step 3: Biometric verification
      setMarkingStep('biometric');
      setStepMessage('Please complete biometric verification...');

      try {
        const bioResult = await attendanceService.verifyBiometric();
        if (bioResult.verified) {
          await attendanceService.markAttendance('GEO_BIOMETRIC', {
            latitude: locationResult.latitude,
            longitude: locationResult.longitude,
            distance: locationResult.distance,
          });
          setMarkingStep('done');
          setStepMessage('Attendance marked via location + biometric');
          toast.success('Attendance marked successfully!');
          fetchData();
          return;
        }
      } catch (bioError) {
        setMarkingStep(null);
        setStepMessage('');
        toast.error(bioError.message || 'Biometric verification failed');
        return;
      }
    } catch (locError) {
      setMarkingStep(null);
      setStepMessage('');
      toast.error(locError.message || 'Location check failed');
    }
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-4 mb-5">
      {/* Header */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="d-flex align-items-center mb-3">
            <Link to="/student/dashboard" className="btn btn-outline-secondary me-3">
              <FontAwesomeIcon icon={faArrowLeft} />
            </Link>
            <div>
              <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                <FontAwesomeIcon icon={faCalendarCheck} /> Attendance
              </h2>
            </div>
          </div>
        </div>
      </div>

      {/* Session Alert */}
      {sessionJustStarted && !todayStatus && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="alert alert-warning d-flex align-items-center justify-content-between mb-0" role="alert">
              <div>
                <FontAwesomeIcon icon={faBell} className="me-2" />
                <strong>Attendance session started!</strong> Mark your attendance now before the session closes.
              </div>
              <button type="button" className="btn-close" onClick={() => setSessionJustStarted(false)} aria-label="Close"></button>
            </div>
          </div>
        </div>
      )}

      {/* Stats */}
      <div className="row mb-4 g-4">
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faCalendarAlt} style={{ color: '#4299e1' }} /> Total Days</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#4299e1' }}>{stats.total}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faCheckCircle} style={{ color: '#48bb78' }} /> Present</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#48bb78' }}>{stats.present}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faTimesCircle} style={{ color: '#e53e3e' }} /> Absent</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#e53e3e' }}>{stats.absent}</h3>
            </div>
          </div>
        </div>
        <div className="col-6 col-md-3">
          <div className="card shadow-sm">
            <div className="card-body">
              <p className="text-muted mb-1"><FontAwesomeIcon icon={faPercentage} style={{ color: '#805ad5' }} /> Percentage</p>
              <h3 className="mb-0 fw-bold" style={{ color: '#805ad5' }}>{stats.percentage}%</h3>
            </div>
          </div>
        </div>
      </div>

      {/* Mark Attendance */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header">
              <h5 className="mb-0"><FontAwesomeIcon icon={faCalendarCheck} /> Today's Attendance</h5>
            </div>
            <div className="card-body text-center py-4">
              {todayStatus ? (
                <div>
                  <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '3rem', color: '#48bb78', marginBottom: '1rem' }} />
                  <h5 className="fw-bold" style={{ color: '#48bb78' }}>Attendance Marked</h5>
                  <p className="text-muted mb-0">
                    Marked at {todayStatus.time} via {todayStatus.method === 'WIFI' ? 'WiFi' : 'Location + Biometric'}
                  </p>
                </div>
              ) : markingStep ? (
                <div>
                  {markingStep === 'done' ? (
                    <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '3rem', color: '#48bb78', marginBottom: '1rem' }} />
                  ) : (
                    <FontAwesomeIcon icon={faSpinner} spin style={{ fontSize: '3rem', color: 'var(--color-primary)', marginBottom: '1rem' }} />
                  )}

                  {/* Step indicators */}
                  <div className="d-flex justify-content-center gap-4 mb-3">
                    <div className="text-center">
                      <FontAwesomeIcon
                        icon={faWifi}
                        style={{
                          fontSize: '1.5rem',
                          color: markingStep === 'wifi' ? '#4299e1' : markingStep === 'location' || markingStep === 'biometric' || markingStep === 'done' ? '#48bb78' : 'var(--color-text-light)',
                        }}
                      />
                      <small className="d-block text-muted">WiFi</small>
                    </div>
                    <div className="text-center">
                      <FontAwesomeIcon
                        icon={faMapMarkerAlt}
                        style={{
                          fontSize: '1.5rem',
                          color: markingStep === 'location' ? '#4299e1' : markingStep === 'biometric' || markingStep === 'done' ? '#48bb78' : 'var(--color-text-light)',
                        }}
                      />
                      <small className="d-block text-muted">Location</small>
                    </div>
                    <div className="text-center">
                      <FontAwesomeIcon
                        icon={faFingerprint}
                        style={{
                          fontSize: '1.5rem',
                          color: markingStep === 'biometric' ? '#4299e1' : markingStep === 'done' ? '#48bb78' : 'var(--color-text-light)',
                        }}
                      />
                      <small className="d-block text-muted">Biometric</small>
                    </div>
                  </div>

                  <p className="text-muted mb-0">{stepMessage}</p>
                </div>
              ) : activeSession ? (
                <div>
                  <p className="text-muted mb-1">Attendance is open</p>
                  <p className="text-muted mb-3" style={{ fontSize: '0.85rem' }}>
                    Started by warden at {activeSession.startedAt}
                  </p>
                  <button className="btn btn-primary btn-lg fw-semibold" onClick={handleMarkAttendance}>
                    <FontAwesomeIcon icon={faCalendarCheck} /> Mark Attendance
                  </button>
                  <div className="mt-3">
                    <small className="text-muted">
                      <FontAwesomeIcon icon={faWifi} /> WiFi &nbsp;|&nbsp;
                      <FontAwesomeIcon icon={faMapMarkerAlt} /> Location + <FontAwesomeIcon icon={faFingerprint} /> Biometric
                    </small>
                  </div>
                </div>
              ) : (
                <div>
                  <FontAwesomeIcon icon={faTimesCircle} style={{ fontSize: '3rem', color: 'var(--color-text-muted)', marginBottom: '1rem' }} />
                  <h5 className="fw-bold text-muted">Attendance Not Active</h5>
                  <p className="text-muted mb-0">The warden has not started attendance yet. You will be able to mark attendance once it is opened.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Attendance History */}
      <div className="row">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header d-flex justify-content-between align-items-center">
              <h5 className="mb-0"><FontAwesomeIcon icon={faHistory} /> Attendance History</h5>
            </div>
            <div className="card-body">
              {history.length === 0 ? (
                <p className="text-center text-muted">No attendance records yet</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Time</th>
                        <th>Method</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.map((record) => (
                        <tr key={record.id}>
                          <td>{record.date}</td>
                          <td>{record.time}</td>
                          <td>{record.method === 'WIFI' ? 'WiFi' : 'Location + Biometric'}</td>
                          <td>
                            <span className={`badge ${record.status === 'PRESENT' ? 'bg-success' : 'bg-danger'}`}>
                              {record.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AttendanceDashboard;
