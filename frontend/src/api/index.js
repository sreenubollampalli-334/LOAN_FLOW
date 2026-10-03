import axiosClient from './axiosClient';

export const authApi = {
  login: (credentials) => axiosClient.post('/api/v1/auth/login', credentials),
  register: (data) => axiosClient.post('/api/v1/auth/register', data)
};

export const customerApi = {
  getProfile: () => axiosClient.get('/api/v1/customers/me'),
  getRepaymentOverview: () => axiosClient.get('/api/v1/loans/repayment-summary'),
  getMyLoans: (page = 0, size = 10) => axiosClient.get(`/api/v1/loans/my-loans?page=${page}&size=${size}`),
  getMyApplications: (page = 0, size = 10) => axiosClient.get(`/api/v1/loan-applications?page=${page}&size=${size}`),
  getLoanProducts: (page = 0, size = 10, sortBy = 'id', direction = 'ASC') => 
    axiosClient.get(`/api/v1/loan-products?page=${page}&size=${size}&sortBy=${sortBy}&direction=${direction}`),
  getLoanProductById: (id) => axiosClient.get(`/api/v1/loan-products/${id}`),
  createLoanApplication: (data) => axiosClient.post('/api/v1/loan-applications', data),
  submitLoanApplication: (id) => axiosClient.post(`/api/v1/loan-applications/${id}/submit`),
  getMyApplicationById: (id) => axiosClient.get(`/api/v1/loan-applications/${id}`),
  getMyLoanById: (id) => axiosClient.get(`/api/v1/loans/${id}`),
  getLoanRepaymentSummary: (id) => axiosClient.get(`/api/v1/loans/${id}/repayment-summary`),
  getLoanSchedule: (id) => axiosClient.get(`/api/v1/loans/${id}/schedule`),
  getLoanPayments: (id, page = 0, size = 10) => axiosClient.get(`/api/v1/loans/${id}/payments?page=${page}&size=${size}`),
  createPayment: (data, idempotencyKey) => axiosClient.post('/api/v1/payments', data, {
    headers: { 'Idempotency-Key': idempotencyKey }
  }),
  getCustomerProfile: () => axiosClient.get('/api/v1/customers/me'),
  updateCustomerProfile: (data) => axiosClient.put('/api/v1/customers/me', data),
  updateLoanApplication: (id, data) => axiosClient.put(`/api/v1/loan-applications/${id}`, data),
  uploadDocument: (applicationId, documentType, file) => {
    const formData = new FormData();
    formData.append('documentType', documentType);
    formData.append('file', file);
    return axiosClient.post(`/api/v1/loan-applications/${applicationId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  getApplicationDocuments: (applicationId) => axiosClient.get(`/api/v1/loan-applications/${applicationId}/documents`),
};

export const officerApi = {
  getReviewQueue: (page = 0, size = 10) => axiosClient.get(`/api/v1/credit-reviews/queue?page=${page}&size=${size}`),
  getReviewDetails: (id) => axiosClient.get(`/api/v1/credit-reviews/applications/${id}`),
  submitReview: (id, data) => axiosClient.post(`/api/v1/credit-reviews/applications/${id}`, data),
  downloadDocument: (applicationId, documentId) => axiosClient.get(`/api/v1/loan-applications/${applicationId}/documents/${documentId}/download`, { responseType: 'blob' }),
  reviewDocument: (applicationId, documentId, data) => axiosClient.post(`/api/v1/credit-reviews/applications/${applicationId}/documents/${documentId}/review`, data),
};

export const managerApi = {
  getReviewQueue: (page = 0, size = 10) => axiosClient.get(`/api/v1/manager-reviews/queue?page=${page}&size=${size}`),
  getReviewDetails: (id) => axiosClient.get(`/api/v1/manager-reviews/applications/${id}`),
  submitReview: (id, data) => axiosClient.post(`/api/v1/manager-reviews/applications/${id}`, data),
  downloadDocument: (applicationId, documentId) => axiosClient.get(`/api/v1/loan-applications/${applicationId}/documents/${documentId}/download`, { responseType: 'blob' })
};

export const adminApi = {
  getDisbursementQueue: (page = 0, size = 10) => axiosClient.get(`/api/v1/disbursements/queue?page=${page}&size=${size}`),
  getDisbursementDetails: (id) => axiosClient.get(`/api/v1/disbursements/${id}`),
  getDisbursementAttempts: (id) => axiosClient.get(`/api/v1/disbursements/${id}/attempts`),
  initiateDisbursement: (loanId, data) => axiosClient.post(`/api/v1/disbursements/loans/${loanId}`, data)
};

export default {
  auth: authApi,
  customer: customerApi,
  officer: officerApi,
  manager: managerApi,
  admin: adminApi
};
