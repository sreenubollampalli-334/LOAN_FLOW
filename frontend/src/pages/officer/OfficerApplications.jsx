import React, { useState, useEffect } from 'react';
import { officerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import { Link } from 'react-router-dom';

const OfficerApplications = () => {
  const [queue, setQueue] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [size] = useState(10);

  useEffect(() => {
    const fetchQueue = async () => {
      try {
        setLoading(true);
        const res = await officerApi.getReviewQueue(page, size);
        setQueue(res.data);
      } catch (err) {
        console.error(err);
        setError(err.response?.data?.message || 'Failed to fetch application queue.');
      } finally {
        setLoading(false);
      }
    };
    fetchQueue();
  }, [page, size]);

  if (loading && !queue) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>Application Review Queue</h2>
      </div>

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        {queue?.content?.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
            <h3>No pending applications</h3>
            <p>The review queue is currently empty.</p>
          </div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #eee', backgroundColor: '#f9f9f9' }}>
                    <th style={{ padding: '12px' }}>App ID</th>
                    <th style={{ padding: '12px' }}>Product</th>
                    <th style={{ padding: '12px' }}>Requested</th>
                    <th style={{ padding: '12px' }}>Term</th>
                    <th style={{ padding: '12px' }}>Submitted</th>
                    <th style={{ padding: '12px' }}>Eligibility</th>
                    <th style={{ padding: '12px' }}>Prior Review?</th>
                    <th style={{ padding: '12px' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {queue.content.map(app => (
                    <tr key={app.applicationId} style={{ borderBottom: '1px solid #eee' }}>
                      <td style={{ padding: '12px', fontWeight: 'bold' }}>#{app.applicationId}</td>
                      <td style={{ padding: '12px' }}>{app.productName}</td>
                      <td style={{ padding: '12px' }}>${app.requestedAmount?.toLocaleString()}</td>
                      <td style={{ padding: '12px' }}>{app.termMonths} mos</td>
                      <td style={{ padding: '12px' }}>{new Date(app.submittedAt).toLocaleString()}</td>
                      <td style={{ padding: '12px' }}>
                        <span style={{ 
                          padding: '4px 8px', 
                          borderRadius: '4px', 
                          fontSize: '12px', 
                          fontWeight: 'bold',
                          backgroundColor: app.eligibilityDecision === 'APPROVED' ? '#d4edda' : '#f8d7da',
                          color: app.eligibilityDecision === 'APPROVED' ? '#155724' : '#721c24'
                        }}>
                          {app.eligibilityDecision}
                        </span>
                      </td>
                      <td style={{ padding: '12px' }}>
                        {app.hasExistingReview ? (
                          <span style={{ color: '#856404', backgroundColor: '#fff3cd', padding: '2px 6px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>Yes</span>
                        ) : (
                          <span style={{ color: '#666', fontSize: '12px' }}>No</span>
                        )}
                      </td>
                      <td style={{ padding: '12px' }}>
                        <Link to={`/officer/applications/${app.applicationId}`} style={{ padding: '6px 12px', backgroundColor: '#0056b3', color: '#fff', textDecoration: 'none', borderRadius: '4px', fontSize: '14px', fontWeight: 'bold' }}>
                          Review
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '20px', paddingTop: '20px', borderTop: '1px solid #eee' }}>
              <div>
                Showing page {queue.pageNumber + 1} of {queue.totalPages || 1} ({queue.totalElements} total applications)
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button 
                  disabled={queue.first}
                  onClick={() => setPage(p => p - 1)}
                  style={{ padding: '8px 16px', backgroundColor: queue.first ? '#f1f1f1' : '#fff', border: '1px solid #ddd', borderRadius: '4px', cursor: queue.first ? 'not-allowed' : 'pointer', color: queue.first ? '#999' : '#333' }}>
                  Previous
                </button>
                <button 
                  disabled={queue.last}
                  onClick={() => setPage(p => p + 1)}
                  style={{ padding: '8px 16px', backgroundColor: queue.last ? '#f1f1f1' : '#fff', border: '1px solid #ddd', borderRadius: '4px', cursor: queue.last ? 'not-allowed' : 'pointer', color: queue.last ? '#999' : '#333' }}>
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default OfficerApplications;
