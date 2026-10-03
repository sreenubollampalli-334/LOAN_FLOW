import React, { useState, useEffect } from 'react';
import { managerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import { Link } from 'react-router-dom';

const ManagerDashboard = () => {
  const [queue, setQueue] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        setLoading(true);
        // Only endpoint available is the queue, so we'll fetch page 0 to get the totalElements stat
        const res = await managerApi.getReviewQueue(0, 5);
        setQueue(res.data);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch dashboard data.');
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <h2 style={{ marginBottom: '20px' }}>Manager Dashboard</h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '20px', marginBottom: '30px' }}>
        <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #ddd', textAlign: 'center' }}>
          <h3 style={{ margin: '0 0 10px 0', color: '#666' }}>Pending Approvals</h3>
          <p style={{ fontSize: '36px', fontWeight: 'bold', color: '#0056b3', margin: 0 }}>
            {queue?.totalElements || 0}
          </p>
        </div>
        <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #ddd', textAlign: 'center' }}>
          <h3 style={{ margin: '0 0 10px 0', color: '#666' }}>Total Approved</h3>
          <p style={{ fontSize: '36px', fontWeight: 'bold', color: '#666', margin: 0 }}>
            --
          </p>
          <small style={{ color: '#999' }}>Not available from backend</small>
        </div>
        <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #ddd', textAlign: 'center' }}>
          <h3 style={{ margin: '0 0 10px 0', color: '#666' }}>Total Rejected</h3>
          <p style={{ fontSize: '36px', fontWeight: 'bold', color: '#666', margin: 0 }}>
            --
          </p>
          <small style={{ color: '#999' }}>Not available from backend</small>
        </div>
      </div>

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h3 style={{ margin: 0 }}>Recent Queue Activity (Top 5)</h3>
          <Link to="/manager/applications" style={{ color: '#0056b3', textDecoration: 'none', fontWeight: 'bold' }}>
            View Full Queue &rarr;
          </Link>
        </div>

        {queue?.content?.length === 0 ? (
          <p style={{ color: '#666' }}>No applications pending manager review.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #eee' }}>
                  <th style={{ padding: '12px' }}>App ID</th>
                  <th style={{ padding: '12px' }}>Product</th>
                  <th style={{ padding: '12px' }}>Requested</th>
                  <th style={{ padding: '12px' }}>Submitted</th>
                  <th style={{ padding: '12px' }}>Eligibility</th>
                  <th style={{ padding: '12px' }}>Officer Rec.</th>
                </tr>
              </thead>
              <tbody>
                {queue.content.map(app => (
                  <tr key={app.applicationId} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: '12px', fontWeight: 'bold' }}>#{app.applicationId}</td>
                    <td style={{ padding: '12px' }}>{app.productName}</td>
                    <td style={{ padding: '12px' }}>${app.requestedAmount?.toLocaleString()}</td>
                    <td style={{ padding: '12px' }}>{new Date(app.submittedAt).toLocaleDateString()}</td>
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
                      <span style={{ 
                        padding: '4px 8px', 
                        borderRadius: '4px', 
                        fontSize: '12px', 
                        fontWeight: 'bold',
                        backgroundColor: app.creditOfficerRecommendation === 'RECOMMEND' ? '#d4edda' : (app.creditOfficerRecommendation === 'DO_NOT_RECOMMEND' ? '#f8d7da' : '#fff3cd'),
                        color: app.creditOfficerRecommendation === 'RECOMMEND' ? '#155724' : (app.creditOfficerRecommendation === 'DO_NOT_RECOMMEND' ? '#721c24' : '#856404')
                      }}>
                        {app.creditOfficerRecommendation}
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
  );
};

export default ManagerDashboard;
