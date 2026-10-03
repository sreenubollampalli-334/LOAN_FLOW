import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { adminApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const AdminDisbursementDetails = () => {
  const { id } = useParams(); // loanId
  const [details, setDetails] = useState(null);
  const [attempts, setAttempts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  // Simulator state
  const [simulateFailure, setSimulateFailure] = useState(false);
  const [failureReason, setFailureReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(null);

  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);
        setError(null);
        // Attempt to load details using id (loanId).
        const res = await adminApi.getDisbursementDetails(id);
        setDetails(res.data);
        
        // Load attempts using the obtained disbursement ID
        if (res.data && res.data.id) {
          const attemptsRes = await adminApi.getDisbursementAttempts(res.data.id);
          setAttempts(attemptsRes.data || []);
        }
      } catch (err) {
        if (err.response?.status === 404) {
          // Disbursement does not exist yet. Safe to ignore and let user initiate it.
          setDetails(null);
        } else {
          console.error(err);
          setError(err.response?.data?.message || 'Failed to load disbursement details.');
        }
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [id]);

  const handleInitiate = async () => {
    try {
      setIsSubmitting(true);
      setSubmitError(null);
      setSubmitSuccess(null);
      
      const payload = {
        simulateFailure: simulateFailure,
        failureReason: simulateFailure ? failureReason : null
      };
      
      const res = await adminApi.initiateDisbursement(id, payload);
      setSubmitSuccess('Disbursement initiated successfully.');
      setDetails(res.data);
      
      if (res.data && res.data.id) {
        const attemptsRes = await adminApi.getDisbursementAttempts(res.data.id);
        setAttempts(attemptsRes.data || []);
      }
    } catch (err) {
      console.error(err);
      if (err.response?.status === 403) {
        setSubmitError('You are not authorized to perform this action.');
      } else if (err.response?.status === 409) {
        setSubmitError(err.response?.data?.message || 'Disbursement cannot be initiated in the current state.');
      } else if (err.response?.status === 400) {
        setSubmitError(err.response?.data?.message || 'Invalid request.');
      } else {
        setSubmitError(err.response?.data?.message || 'Failed to initiate disbursement.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'SUCCESS':
      case 'COMPLETED':
      case 'DISBURSED':
        return { bg: '#d4edda', text: '#155724' };
      case 'PENDING':
        return { bg: '#fff3cd', text: '#856404' };
      case 'FAILED':
      case 'REJECTED':
        return { bg: '#f8d7da', text: '#721c24' };
      default:
        return { bg: '#e2e3e5', text: '#383d41' };
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  const isCompleted = details?.status === 'DISBURSED' || details?.status === 'SUCCESS' || details?.status === 'COMPLETED';
  
  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', color: '#333' }}>
      <div style={{ marginBottom: '20px' }}>
        <Link to="/admin/disbursements" style={{ textDecoration: 'none', color: '#0056b3', fontWeight: 'bold' }}>
          &larr; Back to Disbursements
        </Link>
      </div>

      <h2 style={{ marginBottom: '20px' }}>Admin Disbursement Details</h2>

      {submitSuccess && (
        <div style={{ padding: '15px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '4px', marginBottom: '20px', border: '1px solid #c3e6cb' }}>
          {submitSuccess}
        </div>
      )}

      {submitError && (
        <div style={{ padding: '15px', backgroundColor: '#f8d7da', color: '#721c24', borderRadius: '4px', marginBottom: '20px', border: '1px solid #f5c6cb' }}>
          {submitError}
        </div>
      )}

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '20px' }}>
        <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Disbursement Overview</h3>
        
        {details ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
            <div><strong>Disbursement ID:</strong> {details.id}</div>
            <div><strong>Loan ID:</strong> {details.loanId}</div>
            <div><strong>Amount:</strong> {details.amount != null ? `₹${details.amount.toLocaleString()}` : '--'}</div>
            <div>
              <strong>Status:</strong>{' '}
              <span style={{ 
                padding: '4px 8px', 
                borderRadius: '4px', 
                fontSize: '12px', 
                fontWeight: 'bold',
                backgroundColor: getStatusColor(details.status).bg,
                color: getStatusColor(details.status).text
              }}>
                {details.status || 'UNKNOWN'}
              </span>
            </div>
            <div><strong>Attempts:</strong> {details.attemptsCount}</div>
            <div><strong>Created:</strong> {details.createdAt ? new Date(details.createdAt).toLocaleString() : '--'}</div>
            <div><strong>Disbursed:</strong> {details.disbursedAt ? new Date(details.disbursedAt).toLocaleString() : '--'}</div>
          </div>
        ) : (
          <p style={{ color: '#666' }}>No disbursement record found. You may initiate a simulation below.</p>
        )}
      </div>

      {details?.isDemo && details?.demoDisclaimer && (
        <div style={{ backgroundColor: '#fff3cd', padding: '15px', borderRadius: '8px', border: '1px solid #ffeeba', marginBottom: '20px', color: '#856404' }}>
          <h3 style={{ marginTop: 0, marginBottom: '10px', fontSize: '16px' }}>Demo Information</h3>
          <p style={{ margin: 0 }}><strong>Disclaimer:</strong> {details.demoDisclaimer}</p>
        </div>
      )}

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '20px' }}>
        <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Disbursement Simulator</h3>
        
        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'flex', alignItems: 'center', fontWeight: 'bold', cursor: 'pointer' }}>
            <input 
              type="checkbox" 
              checked={simulateFailure} 
              onChange={(e) => setSimulateFailure(e.target.checked)}
              disabled={isCompleted || isSubmitting}
              style={{ marginRight: '10px' }}
            />
            Simulate Failure
          </label>
        </div>

        {simulateFailure && (
          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '5px' }}>Failure Reason</label>
            <textarea 
              value={failureReason}
              onChange={(e) => setFailureReason(e.target.value)}
              disabled={isCompleted || isSubmitting}
              style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', minHeight: '80px', boxSizing: 'border-box' }}
              placeholder="Enter a reason for simulation failure..."
            />
          </div>
        )}

        <button 
          onClick={handleInitiate}
          disabled={isCompleted || isSubmitting}
          style={{ 
            padding: '10px 20px', 
            backgroundColor: (isCompleted || isSubmitting) ? '#ccc' : '#0056b3', 
            color: '#fff', 
            border: 'none', 
            borderRadius: '4px', 
            cursor: (isCompleted || isSubmitting) ? 'not-allowed' : 'pointer',
            fontWeight: 'bold'
          }}
        >
          {isSubmitting ? 'Processing...' : 'Initiate Disbursement'}
        </button>
      </div>

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Attempt History</h3>
        
        {attempts.length === 0 ? (
          <p style={{ color: '#666' }}>No disbursement attempts recorded.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #eee', backgroundColor: '#f9f9f9' }}>
                  <th style={{ padding: '12px' }}>Attempt #</th>
                  <th style={{ padding: '12px' }}>Status</th>
                  <th style={{ padding: '12px' }}>Failure Reason</th>
                  <th style={{ padding: '12px' }}>Demo</th>
                  <th style={{ padding: '12px' }}>Started At</th>
                  <th style={{ padding: '12px' }}>Completed At</th>
                </tr>
              </thead>
              <tbody>
                {attempts.map(attempt => {
                  const style = getStatusColor(attempt.status);
                  return (
                    <tr key={attempt.id} style={{ borderBottom: '1px solid #eee' }}>
                      <td style={{ padding: '12px' }}>{attempt.attemptNumber}</td>
                      <td style={{ padding: '12px' }}>
                        <span style={{ 
                          padding: '4px 8px', 
                          borderRadius: '4px', 
                          fontSize: '12px', 
                          fontWeight: 'bold',
                          backgroundColor: style.bg,
                          color: style.text
                        }}>
                          {attempt.status || 'UNKNOWN'}
                        </span>
                      </td>
                      <td style={{ padding: '12px' }}>{attempt.failureReason || '--'}</td>
                      <td style={{ padding: '12px' }}>{attempt.isDemo ? 'Yes' : 'No'}</td>
                      <td style={{ padding: '12px' }}>{attempt.startedAt ? new Date(attempt.startedAt).toLocaleString() : '--'}</td>
                      <td style={{ padding: '12px' }}>{attempt.completedAt ? new Date(attempt.completedAt).toLocaleString() : '--'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminDisbursementDetails;
