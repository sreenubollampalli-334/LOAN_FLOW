import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const ApplyLoan = () => {
  const { productId } = useParams();
  const navigate = useNavigate();
  
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const [amount, setAmount] = useState('');
  const [term, setTerm] = useState('');
  const [purpose, setPurpose] = useState('');
  
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getLoanProductById(productId);
        setProduct(res.data);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch product details. Ensure this product is valid.');
      } finally {
        setLoading(false);
      }
    };
    fetchProduct();
  }, [productId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitError(null);
    
    // Basic frontend validation matching backend annotations
    const numAmount = parseFloat(amount);
    const numTerm = parseInt(term, 10);
    
    if (isNaN(numAmount) || numAmount <= 0) {
      setSubmitError('Requested amount must be greater than zero.');
      return;
    }
    if (isNaN(numTerm) || numTerm < 1) {
      setSubmitError('Term must be at least 1 month.');
      return;
    }
    
    // Limits validation based on product
    if (numAmount < product.minAmount || numAmount > product.maxAmount) {
      setSubmitError(`Amount must be between $${product.minAmount} and $${product.maxAmount}.`);
      return;
    }
    if (numTerm < product.minTermMonths || numTerm > product.maxTermMonths) {
      setSubmitError(`Term must be between ${product.minTermMonths} and ${product.maxTermMonths} months.`);
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        productId: product.id,
        requestedAmount: numAmount,
        termMonths: numTerm,
        purpose: purpose
      };
      
      const draftRes = await customerApi.createLoanApplication(payload);
      const appId = draftRes.data.id;
      
      // Submit immediately
      await customerApi.submitLoanApplication(appId);
      
      setSubmitSuccess(true);
      setTimeout(() => {
        navigate('/customer/applications');
      }, 2500);
    } catch (err) {
      console.error(err);
      setSubmitError(err.response?.data?.message || 'Failed to submit application. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!product) return <ErrorMessage message="Product not found" />;

  if (submitSuccess) {
    return (
      <div style={{ maxWidth: '600px', margin: '50px auto', textAlign: 'center', padding: '40px', backgroundColor: '#e6f4ea', borderRadius: '8px', border: '1px solid #cce8d6' }}>
        <h2 style={{ color: '#137333', marginTop: 0 }}>Application Submitted Successfully!</h2>
        <p style={{ color: '#555', fontSize: '16px' }}>Your application has been received and is under review. Redirecting you to your applications...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '600px', margin: '0 auto', backgroundColor: '#fff', padding: '30px', borderRadius: '8px', border: '1px solid #ddd', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold' }}>
        &larr; Back
      </button>
      
      <h2 style={{ marginTop: 0 }}>Apply for {product.name}</h2>
      
      <ErrorMessage message={submitError} />
      
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '20px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>
            Requested Amount ($)
            <span style={{ fontWeight: 'normal', color: '#666', marginLeft: '10px' }}>
              (Min: ${product.minAmount}, Max: ${product.maxAmount})
            </span>
          </label>
          <input 
            type="number" 
            step="0.01"
            value={amount} 
            onChange={e => setAmount(e.target.value)}
            required
            style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box', fontSize: '16px' }}
          />
        </div>
        
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>
            Tenure (Months)
            <span style={{ fontWeight: 'normal', color: '#666', marginLeft: '10px' }}>
              (Min: {product.minTermMonths}, Max: {product.maxTermMonths})
            </span>
          </label>
          <input 
            type="number" 
            value={term} 
            onChange={e => setTerm(e.target.value)}
            required
            style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box', fontSize: '16px' }}
          />
        </div>
        
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>
            Purpose (Optional)
          </label>
          <textarea 
            value={purpose} 
            onChange={e => setPurpose(e.target.value)}
            rows={4}
            style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box', fontFamily: 'inherit', fontSize: '16px' }}
          />
        </div>
        
        <button 
          type="submit" 
          disabled={submitting}
          style={{ 
            padding: '14px', 
            backgroundColor: '#0056b3', 
            color: '#fff', 
            border: 'none', 
            borderRadius: '4px', 
            cursor: submitting ? 'not-allowed' : 'pointer', 
            fontSize: '16px', 
            fontWeight: 'bold',
            opacity: submitting ? 0.7 : 1,
            marginTop: '10px'
          }}>
          {submitting ? 'Submitting Application...' : 'Submit Application'}
        </button>
      </form>
    </div>
  );
};

export default ApplyLoan;
