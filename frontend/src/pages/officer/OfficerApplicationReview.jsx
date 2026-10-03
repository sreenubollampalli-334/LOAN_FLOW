import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { officerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const OfficerApplicationReview = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [decision, setDecision] = useState('');
  const [comments, setComments] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  useEffect(() => {
    const fetchDetails = async () => {
      try {
        setLoading(true);
        const res = await officerApi.getReviewDetails(id);
        setDetails(res.data);
      } catch (err) {
        console.error(err);
        setError(err.response?.data?.message || 'Failed to fetch application review details.');
      } finally {
        setLoading(false);
      }
    };
    fetchDetails();
  }, [id]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitError(null);
    setSubmitSuccess(false);

    if (!decision) {
      setSubmitError('Decision is required.');
      return;
    }
    if (!comments || comments.length < 10 || comments.length > 2000) {
      setSubmitError('Comments must be between 10 and 2000 characters.');
      return;
    }

    try {
      setSubmitting(true);
      await officerApi.submitReview(id, { decision, comments });
      setSubmitSuccess(true);
      setTimeout(() => {
        navigate('/officer/applications');
      }, 2500);
    } catch (err) {
      console.error(err);
      setSubmitError(err.response?.data?.message || 'Failed to submit credit review.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!details) return <ErrorMessage message="Application details not found" />;

  const { application, customer, product, eligibilityAssessment, documents, existingReview } = details;

  if (submitSuccess) {
    return (
      <div style={{ maxWidth: '600px', margin: '50px auto', textAlign: 'center', padding: '40px', backgroundColor: '#e6f4ea', borderRadius: '8px', border: '1px solid #cce8d6' }}>
        <h2 style={{ color: '#137333', marginTop: 0 }}>Review Submitted Successfully!</h2>
        <p style={{ color: '#555', fontSize: '16px' }}>The credit review has been securely logged. Redirecting back to queue...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold', fontSize: '16px' }}>
        &larr; Back to Queue
      </button>

      <h2 style={{ marginTop: 0, marginBottom: '20px' }}>Application #{application.id} Review</h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px', marginBottom: '30px' }}>
        {/* Application Info */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Application Details</h3>
          <p><strong>Status:</strong> {application.status}</p>
          <p><strong>Requested Amount:</strong> ${application.requestedAmount?.toLocaleString()}</p>
          <p><strong>Term:</strong> {application.termMonths} months</p>
          <p><strong>Purpose:</strong> {application.purpose || 'N/A'}</p>
          <p><strong>Submitted At:</strong> {new Date(application.createdAt).toLocaleString()}</p>
        </div>

        {/* Customer Info */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Customer Profile</h3>
          <p><strong>Name:</strong> {customer.firstName} {customer.lastName}</p>
          <p><strong>Email:</strong> {customer.email}</p>
          <p><strong>Customer ID:</strong> {customer.id}</p>
        </div>

        {/* Product Info */}
        <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Loan Product</h3>
          <p><strong>Name:</strong> {product.name}</p>
          <p><strong>Interest Rate:</strong> {product.interestRate}%</p>
          <p><strong>Limits:</strong> ${product.minAmount} - ${product.maxAmount}</p>
          <p><strong>Terms:</strong> {product.minTermMonths} - {product.maxTermMonths} months</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '20px', marginBottom: '30px' }}>
        {/* Eligibility Engine Info */}
        {eligibilityAssessment && (
          <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
            <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Eligibility Assessment</h3>
            <p><strong>Decision:</strong> 
              <span style={{ 
                marginLeft: '10px',
                padding: '4px 8px', 
                borderRadius: '4px', 
                fontSize: '12px', 
                fontWeight: 'bold',
                backgroundColor: eligibilityAssessment.decision === 'APPROVED' ? '#d4edda' : '#f8d7da',
                color: eligibilityAssessment.decision === 'APPROVED' ? '#155724' : '#721c24'
              }}>
                {eligibilityAssessment.decision}
              </span>
            </p>
            <p><strong>Assessed At:</strong> {new Date(eligibilityAssessment.assessedAt).toLocaleString()}</p>
            <p><strong>Ruleset Version:</strong> {eligibilityAssessment.rulesetVersion}</p>
            
            {eligibilityAssessment.reasons && eligibilityAssessment.reasons.length > 0 && (
              <div style={{ marginTop: '15px' }}>
                <strong>Reason Codes:</strong>
                <ul style={{ paddingLeft: '20px', margin: '5px 0' }}>
                  {eligibilityAssessment.reasons.map((reason, idx) => (
                    <li key={idx} style={{ color: '#555' }}>{reason.code} - {reason.description}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* Existing Review (if any) */}
        {existingReview && (
          <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd' }}>
            <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Existing Review</h3>
            <p><strong>Decision:</strong> {existingReview.decision}</p>
            <p><strong>Officer ID:</strong> {existingReview.officerId}</p>
            <p><strong>Reviewed At:</strong> {new Date(existingReview.reviewedAt).toLocaleString()}</p>
            <div style={{ marginTop: '10px' }}>
              <strong>Comments:</strong>
              <div style={{ backgroundColor: '#f5f5f5', padding: '10px', borderRadius: '4px', marginTop: '5px' }}>
                {existingReview.comments}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Documents List */}
      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '30px' }}>
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
                  <th style={{ padding: '10px', textAlign: 'right' }}>Actions</th>
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
                      {doc.reviewStatus === 'VERIFIED' && <span style={{ color: '#155724', fontWeight: 'bold' }}>✓ Verified</span>}
                      {doc.reviewStatus === 'REJECTED' && <span style={{ color: '#721c24', fontWeight: 'bold' }}>✗ Rejected</span>}
                      {(!doc.reviewStatus || doc.reviewStatus === 'PENDING') && <span style={{ color: '#856404', fontWeight: 'bold' }}>Pending</span>}
                      {doc.reviewStatus === 'MISSING' && <span style={{ color: '#666' }}>Missing</span>}
                    </td>
                    <td style={{ padding: '10px', fontSize: '14px', color: '#666' }}>{doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleString() : '--'}</td>
                    <td style={{ padding: '10px', textAlign: 'right' }}>
                      {doc.id && (
                        <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                          <button 
                            onClick={async () => {
                              try {
                                const response = await officerApi.downloadDocument(application.id, doc.id);
                                const url = window.URL.createObjectURL(new Blob([response.data]));
                                const link = document.createElement('a');
                                link.href = url;
                                link.setAttribute('download', doc.originalFilename);
                                document.body.appendChild(link);
                                link.click();
                                document.body.removeChild(link);
                              } catch (err) {
                                alert('Failed to download document: ' + (err.response?.data?.message || err.message));
                              }
                            }}
                            style={{ padding: '4px 8px', backgroundColor: '#e2e3e5', color: '#383d41', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' }}>
                            View/Download
                          </button>
                          
                          {(!doc.reviewStatus || doc.reviewStatus === 'PENDING') && !existingReview && (
                            <>
                              <button 
                                onClick={async () => {
                                  if(window.confirm('Verify this document?')) {
                                    try {
                                      await officerApi.reviewDocument(application.id, doc.id, { status: 'VERIFIED', comments: '' });
                                      const res = await officerApi.getReviewDetails(id);
                                      setDetails(res.data);
                                    } catch (err) {
                                      alert('Verification failed: ' + (err.response?.data?.message || err.message));
                                    }
                                  }
                                }}
                                style={{ padding: '4px 8px', backgroundColor: '#28a745', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' }}>
                                Verify
                              </button>
                              <button 
                                onClick={async () => {
                                  const comment = window.prompt('Please provide a reason for rejecting this document:');
                                  if (comment !== null) {
                                    if (!comment.trim()) {
                                      alert('Rejection reason is required.');
                                      return;
                                    }
                                    try {
                                      await officerApi.reviewDocument(application.id, doc.id, { status: 'REJECTED', comments: comment });
                                      const res = await officerApi.getReviewDetails(id);
                                      setDetails(res.data);
                                    } catch (err) {
                                      alert('Rejection failed: ' + (err.response?.data?.message || err.message));
                                    }
                                  }
                                }}
                                style={{ padding: '4px 8px', backgroundColor: '#dc3545', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' }}>
                                Reject
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Action Form */}
      {!existingReview ? (
        <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '8px', border: '1px solid #ddd' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Submit Credit Review</h3>
          
          <ErrorMessage message={submitError} />
          
          <form onSubmit={handleSubmit} style={{ marginTop: '20px' }}>
            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>Decision *</label>
              <select 
                value={decision}
                onChange={(e) => setDecision(e.target.value)}
                required
                style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', fontSize: '16px' }}
              >
                <option value="" disabled>Select a decision...</option>
                <option value="RECOMMEND">RECOMMEND</option>
                <option value="DO_NOT_RECOMMEND">DO NOT RECOMMEND</option>
                <option value="REQUEST_INFORMATION">REQUEST INFORMATION</option>
              </select>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', marginBottom: '8px', fontWeight: 'bold' }}>
                Comments / Rationale * 
                <span style={{ fontWeight: 'normal', color: '#666', fontSize: '14px', marginLeft: '10px' }}>
                  (10 - 2000 characters)
                </span>
              </label>
              <textarea 
                value={comments}
                onChange={(e) => setComments(e.target.value)}
                required
                rows={5}
                minLength={10}
                maxLength={2000}
                style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box', fontFamily: 'inherit', fontSize: '16px' }}
                placeholder="Provide a detailed rationale for this credit decision..."
              />
            </div>

            <button 
              type="submit" 
              disabled={submitting}
              style={{ 
                padding: '14px 24px', 
                backgroundColor: '#0056b3', 
                color: '#fff', 
                border: 'none', 
                borderRadius: '4px', 
                cursor: submitting ? 'not-allowed' : 'pointer', 
                fontSize: '16px', 
                fontWeight: 'bold',
                opacity: submitting ? 0.7 : 1
              }}>
              {submitting ? 'Submitting...' : 'Submit Credit Review'}
            </button>
          </form>
        </div>
      ) : (
        <div style={{ padding: '20px', backgroundColor: '#e2e3e5', color: '#383d41', borderRadius: '8px', border: '1px solid #d6d8db', textAlign: 'center' }}>
          <strong>Action Complete:</strong> This application has already been reviewed. Further changes are not permitted.
        </div>
      )}
    </div>
  );
};

export default OfficerApplicationReview;
