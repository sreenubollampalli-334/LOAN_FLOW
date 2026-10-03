import React from 'react';

const LoanSummaryCard = ({ loan }) => (
  <div style={{ padding: '15px', border: '1px solid #e0e0e0', borderRadius: '8px', backgroundColor: '#fff', marginBottom: '10px' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
      <h4 style={{ margin: 0, color: '#333' }}>{loan.productName || 'Loan'}</h4>
      <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '12px', backgroundColor: loan.status === 'ACTIVE' ? '#e6f4ea' : '#f1f3f4', color: loan.status === 'ACTIVE' ? '#137333' : '#5f6368', fontWeight: 'bold' }}>
        {loan.status}
      </span>
    </div>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', fontSize: '14px' }}>
      <div>
        <div style={{ color: '#666' }}>Principal Amount</div>
        <div style={{ fontWeight: '500', color: '#333' }}>${loan.principalAmount?.toFixed(2) || '0.00'}</div>
      </div>
      <div>
        <div style={{ color: '#666' }}>Interest Rate</div>
        <div style={{ fontWeight: '500', color: '#333' }}>{loan.interestRate}%</div>
      </div>
      <div>
        <div style={{ color: '#666' }}>Term</div>
        <div style={{ fontWeight: '500', color: '#333' }}>{loan.termMonths} Months</div>
      </div>
    </div>
  </div>
);

export default LoanSummaryCard;
