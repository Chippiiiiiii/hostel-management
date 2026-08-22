import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import axios from 'axios';
import { API_BASE_URL } from '../../utils/constants';

export default function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState(token ? 'loading' : 'error');
  const [message, setMessage] = useState(
    token ? '' : 'No verification token found. Please use the link from your email.'
  );

  useEffect(() => {
    if (!token) return;

    axios.get(`${API_BASE_URL}/auth/verify-email`, { params: { token } })
      .then(() => {
        setStatus('success');
        setMessage('Your email has been verified! You can now log in.');
      })
      .catch(err => {
        setStatus('error');
        setMessage(err.response?.data?.message || 'Verification failed. The link may have expired.');
      });
  }, [token]);

  return (
    <div className="container d-flex justify-content-center align-items-center" style={{ minHeight: '80vh' }}>
      <div className="card shadow-sm text-center p-4" style={{ maxWidth: 420, width: '100%' }}>
        {status === 'loading' && (
          <>
            <div className="spinner-border text-warning mx-auto mb-3" role="status" />
            <p className="text-muted mb-0">Verifying your email…</p>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="mb-3" style={{ fontSize: '3rem' }}>✅</div>
            <h5 className="fw-semibold mb-2">Email Verified!</h5>
            <p className="text-muted mb-4">{message}</p>
            <Link to="/login" className="btn btn-warning fw-semibold">Log In</Link>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="mb-3" style={{ fontSize: '3rem' }}>❌</div>
            <h5 className="fw-semibold mb-2">Verification Failed</h5>
            <p className="text-muted mb-4">{message}</p>
            <Link to="/register" className="btn btn-outline-secondary">Back to Register</Link>
          </>
        )}
      </div>
    </div>
  );
}
