import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import attendanceService from '../../services/attendanceService';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faCalendarCheck, faArrowLeft, faPlay, faStop, faCheckCircle,
  faTimesCircle, faUsers, faClock, faCog, faWifi, faMapMarkerAlt,
  faCheck, faSave, faPlus, faTrash, faDownload, faFileExport,
} from '@fortawesome/free-solid-svg-icons';

const WardenAttendanceDashboard = () => {
  const [activeSession, setActiveSession] = useState(null);
  const [markedStudents, setMarkedStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const pollRef = useRef(null);
  const sessionRef = useRef(null);
  const [showConfig, setShowConfig] = useState(false);
  const [configLoading, setConfigLoading] = useState(false);
  const [attendanceConfig, setAttendanceConfig] = useState({
    wifiAllowedSubnets: '',
    hostelLatitude: '',
    hostelLongitude: '',
    hostelRadius: '50',
  });
  const [showReport, setShowReport] = useState(false);
  const [reportFrom, setReportFrom] = useState('');
  const [reportTo, setReportTo] = useState('');
  const [reportData, setReportData] = useState([]);
  const [reportLoading, setReportLoading] = useState(false);

  useEffect(() => {
    sessionRef.current = activeSession;
  }, [activeSession]);

  useEffect(() => {
    fetchData();
    pollRef.current = setInterval(() => {
      const session = sessionRef.current;
      if (!session?.id) return;
      attendanceService.getSessionRecords(session.id)
        .then(res => setMarkedStudents(res.data))
        .catch(() => {});
    }, 10000);
    return () => clearInterval(pollRef.current);
  }, []);

  const fetchData = async () => {
    try {
      const sessionRes = await attendanceService.getActiveSessionWarden();
      setActiveSession(sessionRes.data);
      if (sessionRes.data?.id) {
        const recordsRes = await attendanceService.getSessionRecords(sessionRes.data.id);
        setMarkedStudents(recordsRes.data);
      }
      const configRes = await attendanceService.getAttendanceConfig();
      if (configRes.data) {
        setAttendanceConfig(configRes.data);
      }
    } catch {
      // silent — individual section errors surface via toast if needed
    } finally {
      setLoading(false);
    }
  };

  const getSubnetList = () => {
    const raw = attendanceConfig.wifiAllowedSubnets || '';
    if (!raw.trim()) return [''];
    return raw.split(',').map(s => s.trim());
  };

  const setSubnetList = (list) => {
    setAttendanceConfig({ ...attendanceConfig, wifiAllowedSubnets: list.filter(s => s.trim()).join(',') });
  };

  const handleSubnetChange = (index, value) => {
    const list = getSubnetList();
    list[index] = value;
    setSubnetList(list);
  };

  const handleAddSubnet = () => {
    const list = getSubnetList();
    list.push('');
    setAttendanceConfig({ ...attendanceConfig, wifiAllowedSubnets: list.join(',') });
  };

  const handleRemoveSubnet = (index) => {
    const list = getSubnetList();
    list.splice(index, 1);
    if (list.length === 0) list.push('');
    setSubnetList(list);
  };

  const handleSaveConfig = async () => {
    setConfigLoading(true);
    try {
      await attendanceService.updateAttendanceConfig({
        wifiAllowedSubnets: attendanceConfig.wifiAllowedSubnets,
        hostelLatitude: attendanceConfig.hostelLatitude,
        hostelLongitude: attendanceConfig.hostelLongitude,
        hostelRadius: attendanceConfig.hostelRadius,
      });
      toast.success('Attendance settings saved');
      setShowConfig(false);
    } catch (error) {
      toast.error('Failed to save settings');
    } finally {
      setConfigLoading(false);
    }
  };

  const handleStartAttendance = async () => {
    try {
      const res = await attendanceService.startSession();
      setActiveSession(res.data);
      setMarkedStudents([]);
      attendanceService.notifySessionStart();
      toast.success('Attendance session started! Students have been notified.');
    } catch (error) {
      toast.error('Failed to start attendance session');
    }
  };

  const handleStopAttendance = async () => {
    try {
      await attendanceService.stopSession();
      setActiveSession(null);
      toast.success('Attendance session closed.');
    } catch (error) {
      toast.error('Failed to stop attendance session');
    }
  };

  const handleFetchReport = async () => {
    if (!reportFrom || !reportTo) {
      toast.error('Please select both dates');
      return;
    }
    setReportLoading(true);
    try {
      const res = await attendanceService.getAttendanceReport(reportFrom, reportTo);
      setReportData(res.data);
      if (res.data.length === 0) toast('No records found for selected dates');
    } catch {
      toast.error('Failed to fetch report');
    } finally {
      setReportLoading(false);
    }
  };

  const handleDownloadCSV = () => {
    if (reportData.length === 0) return;
    const headers = ['Date', 'Time', 'Student Name', 'Roll No', 'Department', 'Method', 'Status'];
    const rows = reportData.map(r => [r.date, r.time, r.studentName, r.rollNo, r.department, r.method, r.status]);
    const csv = [headers, ...rows].map(row => row.map(v => `"${v}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `attendance_${reportFrom}_to_${reportTo}.csv`;
    a.click();
    URL.revokeObjectURL(url);
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
          <div className="d-flex align-items-center justify-content-between mb-3">
            <div className="d-flex align-items-center">
              <Link to="/warden/dashboard" className="btn btn-outline-secondary me-3">
                <FontAwesomeIcon icon={faArrowLeft} />
              </Link>
              <div>
                <h2 className="mb-0" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  <FontAwesomeIcon icon={faCalendarCheck} /> Attendance
                </h2>
              </div>
            </div>
            <button
              className={`btn ${showConfig ? 'btn-primary' : 'btn-outline-primary'} btn-sm`}
              onClick={() => setShowConfig(!showConfig)}
            >
              <FontAwesomeIcon icon={faCog} /> Settings
            </button>
          </div>
        </div>
      </div>

      {/* Attendance Settings Panel */}
      {showConfig && (
        <div className="row mb-4">
          <div className="col-12">
            <div className="card shadow-sm border-primary">
              <div className="card-header bg-primary text-white">
                <h5 className="mb-0"><FontAwesomeIcon icon={faCog} /> Attendance Settings</h5>
              </div>
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-12">
                    <h6 className="fw-bold mb-2"><FontAwesomeIcon icon={faWifi} /> WiFi Configuration</h6>
                  </div>
                  <div className="col-md-12">
                    <label className="form-label fw-semibold">Hostel WiFi IP Ranges</label>
                    {getSubnetList().map((subnet, i) => (
                      <div className="input-group mb-2" key={i}>
                        <input
                          type="text"
                          className="form-control"
                          value={subnet}
                          onChange={(e) => handleSubnetChange(i, e.target.value)}
                          placeholder="e.g. 192.168.1.0/24"
                        />
                        <button
                          className="btn btn-outline-danger"
                          onClick={() => handleRemoveSubnet(i)}
                          disabled={getSubnetList().length === 1 && !subnet}
                          title="Remove"
                        >
                          <FontAwesomeIcon icon={faTrash} />
                        </button>
                      </div>
                    ))}
                    <button className="btn btn-outline-primary btn-sm" onClick={handleAddSubnet}>
                      <FontAwesomeIcon icon={faPlus} /> Add WiFi Network
                    </button>
                    <br />
                    <small className="text-muted">Enter your hostel WiFi IP ranges. Ask your network admin for these. Example: 192.168.1.0/24</small>
                  </div>
                  <div className="col-12 mt-3">
                    <h6 className="fw-bold mb-2"><FontAwesomeIcon icon={faMapMarkerAlt} /> Geolocation Configuration</h6>
                  </div>
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Hostel Latitude</label>
                    <input
                      type="text"
                      className="form-control"
                      value={attendanceConfig.hostelLatitude || ''}
                      onChange={(e) => setAttendanceConfig({ ...attendanceConfig, hostelLatitude: e.target.value })}
                      placeholder="e.g. 12.8231"
                    />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Hostel Longitude</label>
                    <input
                      type="text"
                      className="form-control"
                      value={attendanceConfig.hostelLongitude || ''}
                      onChange={(e) => setAttendanceConfig({ ...attendanceConfig, hostelLongitude: e.target.value })}
                      placeholder="e.g. 80.0444"
                    />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">Radius (meters)</label>
                    <input
                      type="number"
                      className="form-control"
                      value={attendanceConfig.hostelRadius || ''}
                      onChange={(e) => setAttendanceConfig({ ...attendanceConfig, hostelRadius: e.target.value })}
                      placeholder="50"
                      min="10"
                      max="500"
                    />
                    <small className="text-muted">Max distance from hostel to allow attendance</small>
                  </div>
                </div>
                <div className="mt-3">
                  <button className="btn btn-primary" onClick={handleSaveConfig} disabled={configLoading}>
                    {configLoading ? (
                      <span className="spinner-border spinner-border-sm me-1" />
                    ) : (
                      <FontAwesomeIcon icon={faSave} />
                    )} Save Settings
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Session Control */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header">
              <h5 className="mb-0"><FontAwesomeIcon icon={faClock} /> Attendance Session</h5>
            </div>
            <div className="card-body text-center py-4">
              {activeSession ? (
                <div>
                  <FontAwesomeIcon icon={faCheckCircle} style={{ fontSize: '3rem', color: 'var(--color-success)', marginBottom: '1rem' }} />
                  <h5 className="fw-bold" style={{ color: 'var(--color-success)' }}>Attendance is Active</h5>
                  <p className="text-muted mb-3">Started at {activeSession.startedAt}</p>
                  <button className="btn btn-danger btn-lg fw-semibold" onClick={handleStopAttendance}>
                    <FontAwesomeIcon icon={faStop} /> Stop Attendance
                  </button>
                </div>
              ) : (
                <div>
                  <FontAwesomeIcon icon={faTimesCircle} style={{ fontSize: '3rem', color: 'var(--color-text-muted)', marginBottom: '1rem' }} />
                  <h5 className="fw-bold text-muted">No Active Session</h5>
                  <p className="text-muted mb-3">Start attendance so students can mark their presence</p>
                  <button className="btn btn-primary btn-lg fw-semibold" onClick={handleStartAttendance}>
                    <FontAwesomeIcon icon={faPlay} /> Start Attendance
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Attendance Report */}
      <div className="row mb-4">
        <div className="col-12">
          <div className="card shadow-sm">
            <div className="card-header d-flex justify-content-between align-items-center"
              style={{ cursor: 'pointer' }} onClick={() => setShowReport(!showReport)}>
              <h5 className="mb-0"><FontAwesomeIcon icon={faFileExport} /> Attendance Report</h5>
              <span className="text-muted">{showReport ? '▲' : '▼'}</span>
            </div>
            {showReport && (
              <div className="card-body">
                <div className="row g-3 align-items-end mb-3">
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">From Date</label>
                    <input type="date" className="form-control" value={reportFrom}
                      onChange={e => setReportFrom(e.target.value)} />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label fw-semibold">To Date</label>
                    <input type="date" className="form-control" value={reportTo}
                      onChange={e => setReportTo(e.target.value)} />
                  </div>
                  <div className="col-md-4 d-flex gap-2">
                    <button className="btn btn-primary" onClick={handleFetchReport} disabled={reportLoading}>
                      {reportLoading ? <span className="spinner-border spinner-border-sm" /> : 'Fetch'}
                    </button>
                    {reportData.length > 0 && (
                      <button className="btn btn-success" onClick={handleDownloadCSV}>
                        <FontAwesomeIcon icon={faDownload} /> CSV
                      </button>
                    )}
                  </div>
                </div>
                {reportData.length > 0 && (
                  <div className="table-responsive">
                    <table className="table table-hover table-sm">
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Time</th>
                          <th>Name</th>
                          <th>Roll No</th>
                          <th>Department</th>
                          <th>Method</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {reportData.map((r, i) => (
                          <tr key={i}>
                            <td>{r.date}</td>
                            <td>{r.time}</td>
                            <td>{r.studentName}</td>
                            <td>{r.rollNo}</td>
                            <td>{r.department}</td>
                            <td>{r.method === 'WIFI' ? 'WiFi' : 'Location'}</td>
                            <td><span className="badge bg-success">{r.status}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Students who marked attendance */}
      {activeSession && (
        <div className="row">
          <div className="col-12">
            <div className="card shadow-sm">
              <div className="card-header d-flex justify-content-between align-items-center">
                <h5 className="mb-0"><FontAwesomeIcon icon={faUsers} /> Students Marked</h5>
                <span className="badge bg-primary">{markedStudents.length} marked</span>
              </div>
              <div className="card-body">
                {markedStudents.length === 0 ? (
                  <p className="text-center text-muted">No students have marked attendance yet</p>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover">
                      <thead>
                        <tr>
                          <th>Name</th>
                          <th>Roll No</th>
                          <th>Room</th>
                          <th>Time</th>
                          <th>Method</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {markedStudents.map((s, i) => (
                          <tr key={i}>
                            <td>{s.name}</td>
                            <td>{s.rollNo}</td>
                            <td>{s.room}</td>
                            <td>{s.time}</td>
                            <td>{s.method === 'WIFI' ? 'WiFi' : 'Location + Biometric'}</td>
                            <td><span className="badge bg-success">PRESENT</span></td>
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
      )}
    </div>
  );
};

export default WardenAttendanceDashboard;
