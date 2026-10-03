import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import RepaymentSummaryCard from '../../components/customer/RepaymentSummaryCard';
import RepaymentSchedule from '../../components/customer/RepaymentSchedule';
import PaymentHistory from '../../components/customer/PaymentHistory';
import PaymentForm from '../../components/customer/PaymentForm';

const LoanDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  
  const [loan, setLoan] = useState(null);
  const [summary, setSummary] = useState(null);
  const [schedule, setSchedule] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshCount, setRefreshCount] = useState(0);

  const fetchLoanData = useCallback(async () => {
    try {
      setLoading(true);
      const [loanRes, summaryRes, scheduleRes] = await Promise.allSettled([
        customerApi.getMyLoanById(id),
        customerApi.getLoanRepaymentSummary(id),
        customerApi.getLoanSchedule(id)
      ]);
      
      if (loanRes.status === 'fulfilled') setLoan(loanRes.value.data);
      else throw new Error('Could not fetch loan details');
      
      if (summaryRes.status === 'fulfilled') setSummary(summaryRes.value.data);
      if (scheduleRes.status === 'fulfilled') setSchedule(scheduleRes.value.data);

    } catch (err) {
      console.error(err);
      setError('Failed to load loan details.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchLoanData();
  }, [fetchLoanData]);

  const handlePaymentSuccess = () => {
    // Refresh the summary, schedule, and loan details
    fetchLoanData();
    setRefreshCount(prev => prev + 1);
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!loan) return <ErrorMessage message="Loan not found" />;

  let statusBg = '#fef7e0';
  let statusColor = '#b06000';
  if (loan.status === 'ACTIVE') {
    statusBg = '#e6f4ea';
    statusColor = '#137333';
  } else if (loan.status === 'CLOSED' || loan.status === 'SETTLED') {
    statusBg = '#f1f3f4';
    statusColor = '#5f6368';
  } else if (loan.status === 'DEFAULTED') {
    statusBg = '#fce8e6';
    statusColor = '#c5221f';
  }

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold' }}>
        &larr; Back
      </button>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>Loan #{loan.id} - {loan.productName}</h2>
        <span style={{ padding: '6px 12px', borderRadius: '4px', fontSize: '14px', backgroundColor: statusBg, color: statusColor, fontWeight: 'bold' }}>
          {loan.status}
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px', marginBottom: '30px' }}>
        <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Overview</h4>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
            <div>
              <div style={{ color: '#666', fontSize: '14px' }}>Principal</div>
              <div style={{ fontWeight: '500' }}>${loan.principalAmount?.toFixed(2)}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px' }}>Interest Rate</div>
              <div style={{ fontWeight: '500' }}>{loan.interestRate}% ({loan.interestCalculationMethod})</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px' }}>Term</div>
              <div style={{ fontWeight: '500' }}>{loan.termMonths} Months</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px' }}>Created On</div>
              <div style={{ fontWeight: '500' }}>{new Date(loan.createdAt).toLocaleDateString()}</div>
            </div>
          </div>
        </div>

        {summary && <RepaymentSummaryCard summary={summary} />}
      </div>

      <PaymentForm 
        loan={loan} 
        summary={summary} 
        onPaymentSuccess={handlePaymentSuccess} 
      />

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '30px' }}>
        <RepaymentSchedule schedule={schedule} />
      </div>

      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        <PaymentHistory key={refreshCount} loanId={id} />
      </div>
    </div>
  );
};

export default LoanDetails;
