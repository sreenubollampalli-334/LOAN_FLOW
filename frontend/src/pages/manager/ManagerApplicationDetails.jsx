import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { managerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const ManagerApplicationDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [decision, setDecision] = useState('');
  const [comments, setComments] = useState('');
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  useEffect(() => {
    const fetchDetails = async () => {
      try {
        setLoading(true);
        const res = await managerApi.getReviewDetails(id);
        setDetails(res.data);
      } catch (err) {
        console.error(err);
        setError(err.response?.data?.message || 'Failed to fetch manager application details.');
      } finally {
        setLoading(false);
      }
    };
    fetchDetails();
  }, [id]);

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (!decision) {
      setSubmitError('Please select a decision.');
      return;
    }
    if (!comments || comments.length < 10) {
      setSubmitError('Comments must be at least 10 characters.');
      return;
    }

    try {
      setSubmitLoading(true);
      setSubmitError(null);
      await managerApi.submitReview(id, { decision, comments });
      setSubmitSuccess(true);
      setTimeout(() => {
        navigate('/manager/applications');
      }, 2000);
    } catch (err) {
      console.error(err);
      if (err.response?.status === 409) {
        setSubmitError('This application has already received a Manager review or is not in a valid state.');
      } else {
        setSubmitError(err.response?.data?.message || 'Failed to submit review.');
      }
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleDownloadDocument = async (documentId, filename) => {
    try {
      const res = await managerApi.downloadDocument(id, documentId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename || 'document');
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error(err);
      alert('Failed to download document. Please try again later.');
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!details) return <ErrorMessage message="No application details found." />;

  const { application, customer, product, eligibilityAssessment, creditOfficerReview, documents, existingManagerReview } = details;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333', paddingBottom: '50px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>Manager Application Review: #{application?.id}</h2>
        <button 
          onClick={() => navigate('/manager/applications')}
          style={{ padding: '8px 16px', backgroundColor: '#f1f1f1', border: '1px solid #ddd', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
          &larr; Back to Queue
        </button>
      </div>

      {existingManagerReview && (
        <div style={{ backgroundColor: '#d4edda', color: '#155724', padding: '15px', borderRadius: '4px', border: '1px solid #c3e6cb', marginBottom: '20px', fontWeight: 'bold' }}>
          Action Complete: This application has already been reviewed by a Manager.
          (Decision: {existingManagerReview.decision})
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px', marginBottom: '20px' }}>
        
        {/* Application Summary */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Application Summary</h3>
          <p><strong>Status:</strong> {application?.status}</p>
          <p><strong>Requested Amount:</strong> ${application?.requestedAmount?.toLocaleString()}</p>
          <p><strong>Term:</strong> {application?.termMonths} months</p>
          <p><strong>Purpose:</strong> {application?.purpose}</p>
          <p><strong>Submitted:</strong> {new Date(application?.submittedAt).toLocaleString()}</p>
        </div>

        {/* Customer Summary */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Customer Profile</h3>
          <p><strong>Customer ID:</strong> {customer?.id}</p>
          <p><strong>First Name:</strong> {customer?.firstName}</p>
          <p><strong>Last Name:</strong> {customer?.lastName}</p>
          <p><strong>Email:</strong> {customer?.email}</p>
        </div>

        {/* Product Summary */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Product Profile</h3>
          <p><strong>Product:</strong> {product?.name}</p>
          <p><strong>Interest Rate:</strong> {product?.interestRate}%</p>
          <p><strong>Min Amount:</strong> ${product?.minAmount?.toLocaleString()}</p>
          <p><strong>Max Amount:</strong> ${product?.maxAmount?.toLocaleString()}</p>
          <p><strong>Min Term:</strong> {product?.minTermMonths} months</p>
          <p><strong>Max Term:</strong> {product?.maxTermMonths} months</p>
        </div>
        
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '20px', marginBottom: '20px' }}>
        {/* Eligibility Summary */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Eligibility Assessment</h3>
          <p>
            <strong>Decision: </strong> 
            <span style={{ 
              color: eligibilityAssessment?.decision === 'APPROVED' ? '#155724' : '#721c24',
              backgroundColor: eligibilityAssessment?.decision === 'APPROVED' ? '#d4edda' : '#f8d7da',
              padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold'
            }}>
              {eligibilityAssessment?.decision}
            </span>
          </p>
          <p><strong>Ruleset Version:</strong> {eligibilityAssessment?.rulesetVersion}</p>
          <p><strong>Assessed At:</strong> {eligibilityAssessment?.assessedAt ? new Date(eligibilityAssessment.assessedAt).toLocaleString() : '--'}</p>
          
          <h4 style={{ margin: '15px 0 5px 0' }}>Reason Codes:</h4>
          {eligibilityAssessment?.reasons?.length > 0 ? (
            <ul style={{ margin: 0, paddingLeft: '20px', color: '#555' }}>
              {eligibilityAssessment.reasons.map((reason, idx) => <li key={idx}>{reason.code} - {reason.description}</li>)}
            </ul>
          ) : (
            <p style={{ margin: 0, color: '#999', fontStyle: 'italic' }}>None generated</p>
          )}
        </div>

        {/* Credit Officer Review */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Credit Officer Review</h3>
          <p>
            <strong>Recommendation: </strong> 
            <span style={{ 
              color: creditOfficerReview?.decision === 'RECOMMEND' ? '#155724' : (creditOfficerReview?.decision === 'DO_NOT_RECOMMEND' ? '#721c24' : '#856404'),
              backgroundColor: creditOfficerReview?.decision === 'RECOMMEND' ? '#d4edda' : (creditOfficerReview?.decision === 'DO_NOT_RECOMMEND' ? '#f8d7da' : '#fff3cd'),
              padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold'
            }}>
              {creditOfficerReview?.decision || 'PENDING'}
            </span>
          </p>
          <p><strong>Reviewer ID:</strong> {creditOfficerReview?.creditOfficerId}</p>
          <p><strong>Reviewer Email:</strong> {creditOfficerReview?.creditOfficerEmail}</p>
          <p><strong>Comments:</strong> {creditOfficerReview?.comments}</p>
          <p><strong>Reviewed At:</strong> {creditOfficerReview?.reviewedAt ? new Date(creditOfficerReview.reviewedAt).toLocaleString() : '--'}</p>
        </div>
      </div>

      {/* Documents List */}
      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
        <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>
          Documents ({documents ? documents.length : 0})
        </h3>
        {(!documents || documents.length === 0) ? (
          <p style={{ color: '#666' }}>No documents uploaded for this application.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', marginTop: '10px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #eee', backgroundColor: '#f9f9f9' }}>
                  <th style={{ padding: '10px' }}>Document Type</th>
                  <th style={{ padding: '10px' }}>Requirement</th>
                  <th style={{ padding: '10px' }}>Filename</th>
                  <th style={{ padding: '10px' }}>Upload Status</th>
                  <th style={{ padding: '10px' }}>Review Status</th>
                  <th style={{ padding: '10px' }}>Uploaded At</th>
                  <th style={{ padding: '10px' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.documentType} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: '10px', fontWeight: 'bold', color: '#555' }}>{doc.documentType}</td>
                    <td style={{ padding: '10px' }}>
                      {doc.isMandatory ? (
                        <span style={{ color: '#856404', backgroundColor: '#fff3cd', padding: '2px 6px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>Mandatory</span>
                      ) : (
                        <span style={{ color: '#666', fontSize: '12px' }}>Optional</span>
                      )}
                    </td>
                    <td style={{ padding: '10px', color: '#0056b3' }}>{doc.originalFilename || 'Not Uploaded'}</td>
                    <td style={{ padding: '10px' }}>{doc.uploadStatus}</td>
                    <td style={{ padding: '10px' }}>
                      <span style={{ 
                        padding: '4px 8px', 
                        borderRadius: '4px', 
                        fontSize: '12px', 
                        fontWeight: 'bold',
                        backgroundColor: doc.reviewStatus === 'VERIFIED' ? '#d4edda' : (doc.reviewStatus === 'REJECTED' ? '#f8d7da' : '#e2e3e5'),
                        color: doc.reviewStatus === 'VERIFIED' ? '#155724' : (doc.reviewStatus === 'REJECTED' ? '#721c24' : '#383d41')
                      }}>
                        {doc.reviewStatus || 'PENDING'}
                      </span>
                    </td>
                    <td style={{ padding: '10px', fontSize: '14px', color: '#666' }}>{doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleString() : '--'}</td>
                    <td style={{ padding: '10px' }}>
                      {doc.uploadStatus === 'UPLOADED' && doc.id && (
                        <button 
                          onClick={() => handleDownloadDocument(doc.id, doc.originalFilename)}
                          style={{ padding: '6px 12px', backgroundColor: '#f8f9fa', color: '#0056b3', border: '1px solid #0056b3', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' }}>
                          View / Download
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Decision Form */}
      {!existingManagerReview && (
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginTop: '20px' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Manager Decision</h3>
          
          {submitSuccess ? (
            <div style={{ backgroundColor: '#d4edda', color: '#155724', padding: '15px', borderRadius: '4px', border: '1px solid #c3e6cb', fontWeight: 'bold' }}>
              ✓ Decision submitted successfully! Redirecting...
            </div>
          ) : (
            <form onSubmit={handleSubmitReview}>
              {submitError && <ErrorMessage message={submitError} />}
              
              <div style={{ marginBottom: '15px' }}>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Decision *</label>
                <select 
                  value={decision} 
                  onChange={(e) => setDecision(e.target.value)}
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc' }}
                  required
                  disabled={submitLoading}
                >
                  <option value="">Select a decision...</option>
                  <option value="APPROVE">Approve Application</option>
                  <option value="REJECT">Reject Application</option>
                  <option value="RETURN_FOR_REVIEW">Return to Credit Officer for Review</option>
                </select>
              </div>

              <div style={{ marginBottom: '15px' }}>
                <label style={{ display: 'block', marginBottom: '5px', fontWeight: 'bold' }}>Comments / Justification *</label>
                <textarea 
                  value={comments} 
                  onChange={(e) => setComments(e.target.value)}
                  placeholder="Provide detailed justification for this decision (min 10 characters)..."
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', minHeight: '100px', resize: 'vertical' }}
                  required
                  disabled={submitLoading}
                />
                <small style={{ color: '#666' }}>{comments.length} / 2000 characters</small>
              </div>

              <button 
                type="submit" 
                disabled={submitLoading || !decision || comments.length < 10}
                style={{ 
                  padding: '12px 24px', 
                  backgroundColor: submitLoading || !decision || comments.length < 10 ? '#ccc' : '#0056b3', 
                  color: '#fff', 
                  border: 'none', 
                  borderRadius: '4px', 
                  cursor: submitLoading || !decision || comments.length < 10 ? 'not-allowed' : 'pointer',
                  fontWeight: 'bold',
                  fontSize: '16px'
                }}>
                {submitLoading ? 'Submitting...' : 'Submit Decision'}
              </button>
            </form>
          )}
        </div>
      )}

    </div>
  );
};

export default ManagerApplicationDetails;
