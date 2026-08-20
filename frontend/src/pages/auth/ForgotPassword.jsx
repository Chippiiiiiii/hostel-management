import { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faKey, faArrowLeft, faEnvelope, faLock, faCheck } from '@fortawesome/free-solid-svg-icons';

const ForgotPassword = () => {
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('STUDENT');
  const [token, setToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [resetDone, setResetDone] = useState(false);

  const handleRequestReset = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await api.post('/auth/forgot-password', { email, role });
      setToken(res.data.data.token);
      setStep(2);
      toast.success('Reset token generated! Enter your new password.');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to process request');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (newPassword.length < 4) {
      toast.error('Password must be at least 4 characters');
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
      setResetDone(true);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  };

  if (resetDone) {
    return (
      <div className="container mt-5 mb-5">
        <div className="row justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
          <div className="col-md-6 col-lg-5">
            <div className="card shadow-lg card-fade-in">
              <div className="card-body p-5 text-center">
                <FontAwesomeIcon icon={faCheck} style={{ fontSize: '3rem', color: 'var(--color-success)' }} />
                <h3 className="mt-3 fw-bold" style={{ color: 'var(--color-success)' }}>Password Reset!</h3>
                <p className="text-muted mb-4">Your password has been updated. You can now log in with your new password.</p>
                <Link to="/login" className="btn btn-primary btn-lg fw-semibold">
                  Go to Login
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-5 mb-5">
      <div className="row justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="col-md-6 col-lg-5">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              <div className="text-center mb-4">
                <FontAwesomeIcon icon={faKey} style={{ fontSize: '3rem', color: 'var(--color-primary)' }} />
                <h2 className="mb-2" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  {step === 1 ? 'Forgot Password' : 'Reset Password'}
                </h2>
                <p className="text-muted">
                  {step === 1 ? 'Enter your email to reset your password' : 'Set your new password'}
                </p>
              </div>

              {step === 1 ? (
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
                    {loading ? (
                      <><span className="spinner-border spinner-border-sm me-2" /> Processing...</>
                    ) : (
                      'Request Password Reset'
                    )}
                  </button>
                </form>
              ) : (
                <form onSubmit={handleResetPassword}>
                  <div className="mb-4">
                    <label className="form-label"><FontAwesomeIcon icon={faLock} /> New Password</label>
                    <input
                      type="password"
                      className="form-control"
                      value={newPassword}
                      onChange={e => setNewPassword(e.target.value)}
                      placeholder="Enter new password"
                      required
                      minLength={4}
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
                    {loading ? (
                      <><span className="spinner-border spinner-border-sm me-2" /> Resetting...</>
                    ) : (
                      'Reset Password'
                    )}
                  </button>
                </form>
              )}

              <div className="text-center">
                <Link to="/login" className="text-decoration-none" style={{ color: 'var(--color-primary)' }}>
                  <FontAwesomeIcon icon={faArrowLeft} /> Back to Login
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
