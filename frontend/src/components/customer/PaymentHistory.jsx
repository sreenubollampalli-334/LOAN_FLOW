import React, { useState, useEffect } from 'react';
import { customerApi } from '../../api';

const PaymentHistory = ({ loanId }) => {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page] = useState(0);
  const [size] = useState(10);

  useEffect(() => {
    const fetchPayments = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getLoanPayments(loanId, page, size);
        setPayments(res.data.content || []);
      } catch (err) {
        console.error(err);
        setError('Failed to load payment history.');
      } finally {
        setLoading(false);
      }
    };
    if (loanId) {
      fetchPayments();
    }
  }, [loanId, page, size]);

  if (loading) return <p style={{ color: '#666' }}>Loading payment history...</p>;
  if (error) return <p style={{ color: '#c5221f' }}>{error}</p>;

  if (payments.length === 0) {
    return <p style={{ color: '#666' }}>No payment history available for this loan.</p>;
  }

  const getStatusStyle = (status) => {
    switch (status) {
      case 'SUCCESS':
      case 'COMPLETED':
        return { bg: '#e6f4ea', color: '#137333' };
      case 'FAILED':
        return { bg: '#fce8e6', color: '#c5221f' };
      case 'PENDING':
      case 'PROCESSING':
        return { bg: '#fef7e0', color: '#b06000' };
      default:
        return { bg: '#f1f3f4', color: '#5f6368' };
    }
  };

  return (
    <div style={{ overflowX: 'auto', marginTop: '10px' }}>
      <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Recent Payments</h4>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
        <thead>
          <tr style={{ backgroundColor: '#f8f9fa', borderBottom: '2px solid #ddd', textAlign: 'left' }}>
            <th style={{ padding: '10px' }}>Date</th>
            <th style={{ padding: '10px' }}>Reference</th>
            <th style={{ padding: '10px' }}>Amount</th>
            <th style={{ padding: '10px' }}>Status</th>
          </tr>
        </thead>
        <tbody>
          {payments.map(payment => {
            const statusStyle = getStatusStyle(payment.status);
            return (
              <tr key={payment.id} style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '10px' }}>{new Date(payment.paymentDate).toLocaleString()}</td>
                <td style={{ padding: '10px', color: '#555' }}>{payment.referenceNumber || 'N/A'}</td>
                <td style={{ padding: '10px', fontWeight: 'bold' }}>${payment.amount?.toFixed(2)}</td>
                <td style={{ padding: '10px' }}>
                  <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '12px', backgroundColor: statusStyle.bg, color: statusStyle.color, fontWeight: 'bold' }}>
                    {payment.status}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default PaymentHistory;
