import React, { useState } from 'react';
import { customerApi } from '../../api';
import ErrorMessage from '../ErrorMessage';

const PaymentForm = ({ loan, summary, onPaymentSuccess }) => {
  const [amount, setAmount] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [successData, setSuccessData] = useState(null);

  if (!loan) return null;

  if (loan.status === 'CLOSED' || loan.status === 'SETTLED') {
    return (
      <div style={{ backgroundColor: '#f1f3f4', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', textAlign: 'center', marginBottom: '30px' }}>
        <h3 style={{ color: '#5f6368', margin: 0 }}>Loan fully paid</h3>
      </div>
    );
  }

  // Allow payment if status is DISBURSED or ACTIVE (to handle older test data)
  if (loan.status !== 'DISBURSED' && loan.status !== 'ACTIVE') {
    return null; // The backend remains authoritative, but we hide it for unsupported statuses like DEFAULTED or pending ones
  }

  const outstandingAmount = summary ? summary.totalOutstanding : 0;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessData(null);

    const numAmount = parseFloat(amount);
    if (!amount || isNaN(numAmount) || numAmount <= 0) {
      setError('Please enter a valid amount greater than 0.');
      return;
    }

    if (outstandingAmount > 0 && numAmount > outstandingAmount) {
      setError(`Amount exceeds the total outstanding balance of $${outstandingAmount.toFixed(2)}.`);
      return;
    }

    setIsSubmitting(true);
    const idempotencyKey = crypto.randomUUID();

    try {
      const response = await customerApi.createPayment(
        { loanId: loan.id, amount: numAmount },
        idempotencyKey
      );

      setSuccessData({
        amount: response.data.amount,
        status: response.data.status,
        referenceNumber: response.data.referenceNumber
      });
      setAmount('');
      
      if (onPaymentSuccess) {
        onPaymentSuccess();
      }
    } catch (err) {
      console.error('Payment error', err);
      let errMsg = 'An unexpected error occurred during payment submission.';
      
      if (err.response) {
        const { status, data } = err.response;
        if (status === 409) {
          errMsg = data.message || 'Payment conflict or duplicate request detected. Please try again.';
        } else if (status === 400) {
          errMsg = data.message || 'Invalid payment request.';
        } else if (status === 401 || status === 403) {
          errMsg = 'You are not authorized to make this payment.';
        } else {
          errMsg = data.message || errMsg;
        }
      }
      setError(errMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '30px' }}>
      <h3 style={{ margin: '0 0 15px 0', color: '#333' }}>Make a Payment</h3>
      
      {successData && (
        <div style={{ backgroundColor: '#e6f4ea', color: '#137333', padding: '15px', borderRadius: '4px', marginBottom: '15px', border: '1px solid #ceead6' }}>
          <h4 style={{ margin: '0 0 5px 0' }}>Payment Successful!</h4>
          <p style={{ margin: '0 0 5px 0' }}><strong>Amount:</strong> ${successData.amount.toFixed(2)}</p>
          <p style={{ margin: '0 0 5px 0' }}><strong>Status:</strong> {successData.status}</p>
          <p style={{ margin: 0 }}><strong>Reference Number:</strong> {successData.referenceNumber}</p>
        </div>
      )}

      {error && <ErrorMessage message={error} />}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px', maxWidth: '400px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '5px', fontWeight: '500', color: '#555' }}>
            Payment Amount ($)
          </label>
          <input
            type="number"
            step="0.01"
            min="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            disabled={isSubmitting}
            placeholder="0.00"
            style={{
              width: '100%',
              padding: '10px',
              border: '1px solid #ccc',
              borderRadius: '4px',
              fontSize: '16px',
              boxSizing: 'border-box'
            }}
          />
          {summary && outstandingAmount > 0 && (
            <div style={{ fontSize: '12px', color: '#666', marginTop: '5px' }}>
              Outstanding Balance: ${outstandingAmount.toFixed(2)}
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting || !amount}
          style={{
            padding: '12px',
            backgroundColor: isSubmitting ? '#9ca3af' : '#0056b3',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            fontSize: '16px',
            fontWeight: 'bold',
            cursor: isSubmitting ? 'not-allowed' : 'pointer'
          }}
        >
          {isSubmitting ? 'Processing...' : 'Submit Payment'}
        </button>
      </form>
    </div>
  );
};

export default PaymentForm;
