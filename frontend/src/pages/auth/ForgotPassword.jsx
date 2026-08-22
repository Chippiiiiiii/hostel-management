import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../../services/api';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faKey, faArrowLeft, faEnvelope, faLock, faCheck } from '@fortawesome/free-solid-svg-icons';

const ForgotPassword = () => {
  const [searchParams] = useSearchParams();
  const urlToken = searchParams.get('token');

  // 'request' | 'sent' | 'reset' | 'done'
  const [view, setView] = useState(urlToken ? 'reset' : 'request');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('STUDENT');
  const [token, setToken] = useState(urlToken || '');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (urlToken) {
      setToken(urlToken);
      setView('reset');
    }
  }, [urlToken]);

  const handleRequestReset = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await api.post('/auth/forgot-password', { email, role });
      setView('sent');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to process request');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (newPassword.length < 6) {
      toast.error('Password must be at least 6 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      await api.post('/auth/reset-password', { token, newPassword });
      toast.success('Password reset successfully!');
      setView('done');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  };

  const renderContent = () => {
    switch (view) {
      case 'sent':
        return (
          <div className="text-center">
            <div style={{ fontSize: '3.5rem' }}>📧</div>
            <h4 className="fw-bold mt-3 mb-2">Check Your Email</h4>
            <p className="text-muted mb-4">
              We sent a password reset link to <strong>{email}</strong>. It expires in 15 minutes.
            </p>
            <p className="text-muted" style={{ fontSize: '0.85rem' }}>
              Didn&apos;t receive it? Check your spam folder or{' '}
              <button className="btn btn-link p-0" style={{ fontSize: '0.85rem' }} onClick={() => setView('request')}>
                try again
              </button>.
            </p>
          </div>
        );

      case 'reset':
        return (
          <form onSubmit={handleResetPassword}>
            <div className="mb-4">
              <label className="form-label"><FontAwesomeIcon icon={faLock} /> New Password</label>
              <input
                type="password"
                className="form-control"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                placeholder="Enter new password (min. 6 characters)"
                required
                minLength={6}
              />
            </div>
            <div className="mb-4">
              <label className="form-label"><FontAwesomeIcon icon={faLock} /> Confirm Password</label>
              <input
                type="password"
                className="form-control"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                placeholder="Confirm new password"
                required
              />
            </div>
            <button type="submit" className="btn btn-primary w-100 mb-3"
              disabled={loading} style={{ minHeight: '50px', fontWeight: '600' }}>
              {loading
                ? <><span className="spinner-border spinner-border-sm me-2" /> Resetting...</>
                : 'Reset Password'}
            </button>
          </form>
        );

      case 'done':
        return (
          <div className="text-center">
            <FontAwesomeIcon icon={faCheck} style={{ fontSize: '3rem', color: 'var(--color-success)' }} />
            <h3 className="mt-3 fw-bold" style={{ color: 'var(--color-success)' }}>Password Reset!</h3>
            <p className="text-muted mb-4">Your password has been updated. You can now log in with your new password.</p>
            <Link to="/login" className="btn btn-primary btn-lg fw-semibold">Go to Login</Link>
          </div>
        );

      default: // 'request'
        return (
          <form onSubmit={handleRequestReset}>
            <div className="mb-4">
              <label className="form-label">Role</label>
              <select className="form-select" value={role} onChange={e => setRole(e.target.value)}>
                <option value="STUDENT">Student</option>
                <option value="WARDEN">Warden</option>
                <option value="SECURITY_GUARD">Security Guard</option>
              </select>
            </div>
            <div className="mb-4">
              <label className="form-label"><FontAwesomeIcon icon={faEnvelope} /> Email Address</label>
              <input
                type="email"
                className="form-control"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="your.email@example.com"
                required
              />
            </div>
            <button type="submit" className="btn btn-primary w-100 mb-3"
              disabled={loading} style={{ minHeight: '50px', fontWeight: '600' }}>
              {loading
                ? <><span className="spinner-border spinner-border-sm me-2" /> Processing...</>
                : 'Send Reset Link'}
            </button>
          </form>
        );
    }
  };

  const headingMap = {
    request: 'Forgot Password',
    sent: null,
    reset: 'Reset Password',
    done: null,
  };

  const subtitleMap = {
    request: 'Enter your email to receive a reset link',
    reset: 'Set your new password',
  };

  return (
    <div className="container mt-5 mb-5">
      <div className="row justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="col-md-6 col-lg-5">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              {headingMap[view] && (
                <div className="text-center mb-4">
                  <FontAwesomeIcon icon={faKey} style={{ fontSize: '3rem', color: 'var(--color-primary)' }} />
                  <h2 className="mb-2" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                    {headingMap[view]}
                  </h2>
                  {subtitleMap[view] && <p className="text-muted">{subtitleMap[view]}</p>}
                </div>
              )}

              {renderContent()}

              {view !== 'done' && view !== 'sent' && (
                <div className="text-center">
                  <Link to="/login" className="text-decoration-none" style={{ color: 'var(--color-primary)' }}>
                    <FontAwesomeIcon icon={faArrowLeft} /> Back to Login
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
