import React from 'react';

const RepaymentSchedule = ({ schedule }) => {
  if (!schedule || schedule.length === 0) {
    return <p style={{ color: '#666' }}>No repayment schedule available.</p>;
  }

  const getStatusColor = (status) => {
    switch (status) {
      case 'PAID': return { bg: '#e6f4ea', color: '#137333' };
      case 'OVERDUE': return { bg: '#fce8e6', color: '#c5221f' };
      case 'PENDING': return { bg: '#fef7e0', color: '#b06000' };
      default: return { bg: '#f1f3f4', color: '#5f6368' };
    }
  };

  return (
    <div style={{ overflowX: 'auto', marginTop: '10px' }}>
      <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Repayment Schedule</h4>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
        <thead>
          <tr style={{ backgroundColor: '#f8f9fa', borderBottom: '2px solid #ddd', textAlign: 'left' }}>
            <th style={{ padding: '10px' }}>No.</th>
            <th style={{ padding: '10px' }}>Due Date</th>
            <th style={{ padding: '10px' }}>Principal</th>
            <th style={{ padding: '10px' }}>Interest</th>
            <th style={{ padding: '10px' }}>Total</th>
            <th style={{ padding: '10px' }}>Status</th>
          </tr>
        </thead>
        <tbody>
          {schedule.map(installment => {
            const statusStyle = getStatusColor(installment.status);
            return (
              <tr key={installment.id || installment.installmentNumber} style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '10px' }}>{installment.installmentNumber}</td>
                <td style={{ padding: '10px' }}>{installment.dueDate}</td>
                <td style={{ padding: '10px' }}>${installment.principalAmount?.toFixed(2) || '0.00'}</td>
                <td style={{ padding: '10px' }}>${installment.interestAmount?.toFixed(2) || '0.00'}</td>
                <td style={{ padding: '10px', fontWeight: 'bold' }}>${installment.totalAmount?.toFixed(2) || '0.00'}</td>
                <td style={{ padding: '10px' }}>
                  <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '12px', backgroundColor: statusStyle.bg, color: statusStyle.color, fontWeight: 'bold' }}>
                    {installment.status}
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

export default RepaymentSchedule;
