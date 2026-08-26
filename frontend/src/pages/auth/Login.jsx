import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import toast from 'react-hot-toast';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGraduationCap, faUserGraduate, faUserTie, faShieldAlt, faArrowRight, faUserShield } from '@fortawesome/free-solid-svg-icons';

const Login = () => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    role: 'STUDENT',
  });
  const [loading, setLoading] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState('');
  const [resending, setResending] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      await login(formData.email, formData.password, formData.role);
      toast.success('Login successful!');
      
      // Navigate based on role
      switch (formData.role) {
        case 'STUDENT':
          navigate('/student/dashboard');
          break;
        case 'WARDEN':
          navigate('/warden/dashboard');
          break;
        case 'SECURITY_GUARD':
          navigate('/security/dashboard');
          break;
        case 'ADMIN':
          navigate('/admin/dashboard');
          break;
        default:
          navigate('/');
      }
    } catch (error) {
      const msg = error.response?.data?.message || '';
      if (msg === 'EMAIL_NOT_VERIFIED') {
        setUnverifiedEmail(formData.email);
      } else {
        toast.error(msg || 'Login failed. Please check your credentials.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    setResending(true);
    try {
      await api.post('/auth/resend-verification', { email: unverifiedEmail });
      toast.success('Verification email sent! Check your inbox.');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Failed to resend. Please try again.');
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="container mt-5 mb-5">
      <div className="row justify-content-center align-items-center" style={{ minHeight: '70vh' }}>
        <div className="col-md-6 col-lg-5">
          <div className="card shadow-lg card-fade-in">
            <div className="card-body p-5">
              <div className="text-center mb-4">
                <FontAwesomeIcon icon={faGraduationCap} style={{ fontSize: '3rem', color: 'var(--color-primary)' }} />
                <h2 className="mb-2" style={{ fontWeight: '700', color: 'var(--color-primary)' }}>
                  Hostel Management
                </h2>
                <p className="text-muted">Login to Your Account</p>
              </div>

              <form onSubmit={handleSubmit}>
                <div className="mb-4">
                  <label className="form-label">Role</label>
                  <select
                    className="form-select"
                    name="role"
                    value={formData.role}
                    onChange={handleChange}
                    required
                  >
                    <option value="STUDENT"><FontAwesomeIcon icon={faUserGraduate} /> Student</option>
                    <option value="WARDEN"><FontAwesomeIcon icon={faUserTie} /> Warden</option>
                    <option value="SECURITY_GUARD"><FontAwesomeIcon icon={faShieldAlt} /> Security Guard</option>
                    <option value="ADMIN"><FontAwesomeIcon icon={faUserShield} /> Admin</option>
                  </select>
                </div>

                <div className="mb-4">
                  <label className="form-label">Email Address</label>
                  <input
                    type="email"
                    className="form-control"
                    name="email"
                    value={formData.email}
                    onChange={handleChange}
                    placeholder="your.email@example.com"
                    required
                  />
                </div>

                <div className="mb-4">
                  <label className="form-label">Password</label>
                  <input
                    type="password"
                    className="form-control"
                    name="password"
                    value={formData.password}
                    onChange={handleChange}
                    placeholder="Enter your password"
                    required
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 mb-3"
                  disabled={loading}
                  style={{ minHeight: '50px', fontSize: '1rem', fontWeight: '600' }}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                      Logging in...
                    </>
                  ) : (
                    <><FontAwesomeIcon icon={faArrowRight} /> Login</>
                  )}
                </button>

                {unverifiedEmail && (
                  <div className="alert alert-warning py-2 mb-3">
                    <strong>Email not verified.</strong> Please check your inbox for a verification link.
                    <button
                      type="button"
                      className="btn btn-link p-0 ms-2"
                      style={{ fontSize: '0.875rem', verticalAlign: 'baseline' }}
                      onClick={handleResendVerification}
                      disabled={resending}
                    >
                      {resending ? 'Sending…' : 'Resend email'}
                    </button>
                  </div>
                )}

                <div className="text-center mb-3">
                  <Link
                    to="/forgot-password"
                    className="text-decoration-none"
                    style={{ color: 'var(--color-primary)', fontSize: '0.9rem' }}
                  >
                    Forgot Password?
                  </Link>
                </div>

                <div className="text-center pt-3" style={{ borderTop: '1px solid var(--color-accent)' }}>
                  <p className="mb-0 text-muted">
                    Don't have an account?{' '}
                    <Link
                      to="/register"
                      className="text-decoration-none fw-bold"
                      style={{ color: 'var(--color-primary)' }}
                    >
                      Register here
                    </Link>
                  </p>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
