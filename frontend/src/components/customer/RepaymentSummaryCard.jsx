import React from 'react';

const RepaymentSummaryCard = ({ summary }) => {
  return (
    <div style={{ padding: '20px', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#fff' }}>
      <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Repayment Summary</h4>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: '15px' }}>
        <div>
          <div style={{ color: '#666', fontSize: '14px' }}>Outstanding Balance</div>
          <div style={{ fontWeight: 'bold', fontSize: '18px' }}>${summary.outstandingBalance?.toFixed(2) || '0.00'}</div>
        </div>
        <div>
          <div style={{ color: '#666', fontSize: '14px' }}>Total Paid</div>
          <div style={{ fontWeight: 'bold', fontSize: '18px', color: '#137333' }}>${summary.totalPaid?.toFixed(2) || '0.00'}</div>
        </div>
        <div>
          <div style={{ color: '#666', fontSize: '14px' }}>Total Scheduled</div>
          <div style={{ fontWeight: 'bold', fontSize: '18px' }}>${summary.totalScheduledAmount?.toFixed(2) || '0.00'}</div>
        </div>
        <div>
          <div style={{ color: '#666', fontSize: '14px' }}>Progress</div>
          <div style={{ fontWeight: 'bold', fontSize: '18px' }}>
            {summary.paidInstallments || 0} / {(summary.paidInstallments || 0) + (summary.pendingInstallments || 0)}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RepaymentSummaryCard;
