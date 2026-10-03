import React, { useState, useEffect } from 'react';
import { adminApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import { Link } from 'react-router-dom';

const AdminDisbursements = () => {
  const [queue, setQueue] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [size] = useState(10);

  useEffect(() => {
    const fetchQueue = async () => {
      try {
        setLoading(true);
        const res = await adminApi.getDisbursementQueue(page, size);
        setQueue(res.data);
      } catch (err) {
        console.error(err);
        setError(err.response?.data?.message || 'Failed to fetch disbursement queue.');
      } finally {
        setLoading(false);
      }
    };
    fetchQueue();
  }, [page, size]);

  const getStatusColor = (status) => {
    switch (status) {
      case 'APPROVED':
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

  if (loading && !queue) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>Admin Disbursement Queue</h2>
      </div>

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        {queue?.content?.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
            <h3>No loans currently pending disbursement.</h3>
          </div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #eee', backgroundColor: '#f9f9f9' }}>
                    <th style={{ padding: '12px' }}>Loan ID</th>
                    <th style={{ padding: '12px' }}>App ID</th>
                    <th style={{ padding: '12px' }}>Customer</th>
                    <th style={{ padding: '12px' }}>Loan Product</th>
                    <th style={{ padding: '12px' }}>Principal Amount</th>
                    <th style={{ padding: '12px' }}>Term</th>
                    <th style={{ padding: '12px' }}>Loan Status</th>
                    <th style={{ padding: '12px' }}>Disbursement Status</th>
                    <th style={{ padding: '12px' }}>Approved At</th>
                    <th style={{ padding: '12px' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {queue.content.map(item => {
                    const loanStyle = getStatusColor(item.loanStatus);
                    const disbStyle = getStatusColor(item.disbursementStatus);
                    return (
                      <tr key={item.loanId} style={{ borderBottom: '1px solid #eee' }}>
                        <td style={{ padding: '12px', fontWeight: 'bold' }}>#{item.loanId}</td>
                        <td style={{ padding: '12px' }}>#{item.applicationId}</td>
                        <td style={{ padding: '12px' }}>{item.customerName}</td>
                        <td style={{ padding: '12px' }}>{item.productName}</td>
                        <td style={{ padding: '12px' }}>₹{item.principalAmount?.toLocaleString()}</td>
                        <td style={{ padding: '12px' }}>{item.termMonths} mos</td>
                        <td style={{ padding: '12px' }}>
                          <span style={{ 
                            padding: '4px 8px', 
                            borderRadius: '4px', 
                            fontSize: '12px', 
                            fontWeight: 'bold',
                            backgroundColor: loanStyle.bg,
                            color: loanStyle.text
                          }}>
                            {item.loanStatus || 'UNKNOWN'}
                          </span>
                        </td>
                        <td style={{ padding: '12px' }}>
                          <span style={{ 
                            padding: '4px 8px', 
                            borderRadius: '4px', 
                            fontSize: '12px', 
                            fontWeight: 'bold',
                            backgroundColor: disbStyle.bg,
                            color: disbStyle.text
                          }}>
                            {item.disbursementStatus || 'UNKNOWN'}
                          </span>
                        </td>
                        <td style={{ padding: '12px' }}>
                          {item.approvedAt ? new Date(item.approvedAt).toLocaleString() : '--'}
                        </td>
                        <td style={{ padding: '12px' }}>
                          <Link to={`/admin/disbursements/${item.loanId}`} style={{ padding: '6px 12px', backgroundColor: '#0056b3', color: '#fff', textDecoration: 'none', borderRadius: '4px', fontSize: '14px', fontWeight: 'bold', display: 'inline-block', whiteSpace: 'nowrap' }}>
                            View
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '20px', paddingTop: '20px', borderTop: '1px solid #eee' }}>
              <div>
                Showing page {queue.pageNumber + 1} of {queue.totalPages || 1} ({queue.totalElements} total records)
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

export default AdminDisbursements;
