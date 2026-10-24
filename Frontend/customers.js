'use strict';

const customerApi = '/api/customers';
const customerTypes = { NORMAL: 'Khách thường', STUDENT: 'Sinh viên', VIP: 'Thành viên VIP' };
const customerFields = ['fullName', 'phone', 'email', 'customerType', 'studentId'];
const moneyFormatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 2 });
const percentFormatter = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 2 });
const elements = {};
let customers = [];
let discountPolicies = null;
let editingCustomerId = null;
let quotedCustomerId = null;
let savingCustomer = false;
let loadVersion = 0;
let studentCheckVersion = 0;
let quoteVersion = 0;

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[id]').forEach(element => { elements[element.id] = element; });
    elements.addCustomer.addEventListener('click', () => openCustomerForm());
    elements.refreshCustomers.addEventListener('click', loadCustomers);
    elements.searchCustomer.addEventListener('input', renderCustomers);
    elements.filterType.addEventListener('change', renderCustomers);
    elements.customerType.addEventListener('change', updateCustomerType);
    elements.customerForm.addEventListener('submit', saveCustomer);
    elements.cancelCustomer.addEventListener('click', closeCustomerForm);
    elements.closeCustomerDialog.addEventListener('click', closeCustomerForm);
    elements.customerDialog.addEventListener('cancel', event => {
        if (savingCustomer) event.preventDefault();
    });
    elements.customerDialog.addEventListener('close', invalidateStudentCheck);
    elements.checkStudentId.addEventListener('click', checkStudentId);
    elements.studentId.addEventListener('input', invalidateStudentCheck);
    elements.closePriceDialog.addEventListener('click', () => elements.priceDialog.close());
    elements.priceDialog.addEventListener('close', invalidateQuote);
    elements.priceForm.addEventListener('submit', calculatePrice);
    elements.basePrice.addEventListener('input', invalidateQuote);
    elements.seatSurcharge.addEventListener('input', invalidateQuote);
    customerFields.forEach(field => elements[field].addEventListener('input', () => clearFieldError(field)));
    loadCustomers();
});

async function requestApi(path = '', options = {}) {
    if (!['http:', 'https:'].includes(location.protocol)) {
        throw new Error('Hãy chạy backend rồi mở http://localhost:8080/customers.html để sử dụng màn hình này.');
    }
    let response;
    try {
        response = await fetch(`${customerApi}${path}`, {
            ...options,
            headers: { 'Content-Type': 'application/json', ...options.headers }
        });
    } catch {
        throw new Error('Không thể kết nối máy chủ. Vui lòng kiểm tra backend và thử lại.');
    }
    let data;
    try {
        data = await response.json();
    } catch {
        throw new Error('Máy chủ trả về dữ liệu không hợp lệ. Vui lòng thử lại.');
    }
    if (!response.ok) {
        const error = new Error(data.message || 'Không thể thực hiện yêu cầu. Vui lòng thử lại.');
        error.fieldErrors = data.fieldErrors || {};
        throw error;
    }
    return data;
}

async function loadCustomers() {
    const version = ++loadVersion;
    elements.refreshCustomers.disabled = true;
    elements.addCustomer.disabled = true;
    elements.tableState.textContent = 'Đang tải khách hàng…';
    elements.tableState.hidden = false;
    try {
        const [loadedCustomers, loadedPolicies] = await Promise.all([requestApi(), requestApi('/policies')]);
        if (version !== loadVersion) return;
        customers = loadedCustomers;
        discountPolicies = loadedPolicies;
        renderCustomers();
        updateStatistics();
        elements.notice.hidden = true;
    } catch (error) {
        if (version !== loadVersion) return;
        elements.tableState.textContent = 'Không tải được dữ liệu mới. Nhấn “Tải lại” để thử lại.';
        elements.tableState.hidden = false;
        elements.resultCount.textContent = 'Chưa cập nhật được danh sách';
        showNotice(error.message, true);
    } finally {
        if (version === loadVersion) {
            elements.refreshCustomers.disabled = false;
            elements.addCustomer.disabled = discountPolicies === null;
        }
    }
}

function updateStatistics() {
    elements.totalCount.textContent = customers.length;
    for (const type of Object.keys(customerTypes)) {
        const prefix = type.toLowerCase();
        elements[`${prefix}Count`].textContent = customers.filter(customer => customer.customerType === type).length;
        elements[`${prefix}Policy`].textContent = discountPolicies[type] > 0
            ? `Ưu đãi ${percentFormatter.format(discountPolicies[type])} trên giá vé`
            : 'Áp dụng giá vé tiêu chuẩn';
    }
}

function normalizeSearch(value) {
    return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/đ/g, 'd');
}

function renderCustomers() {
    const keyword = normalizeSearch(elements.searchCustomer.value.trim());
    const selectedType = elements.filterType.value;
    const filtered = customers.filter(customer => (!selectedType || customer.customerType === selectedType)
        && [customer.id, customer.fullName, customer.phone, customer.email, customer.studentId || '']
            .some(value => normalizeSearch(value).includes(keyword)))
        .sort((first, second) => first.fullName.localeCompare(second.fullName, 'vi'));
    const fragment = document.createDocumentFragment();
    for (const customer of filtered) {
        const row = document.createElement('tr');
        const identity = document.createElement('td');
        identity.append(textElement('div', customer.fullName, 'customer-name'));
        const id = textElement('div', customer.id, 'customer-id');
        id.title = customer.id;
        identity.append(id);
        const contact = document.createElement('td');
        contact.append(textElement('div', customer.phone), textElement('div', customer.email, 'contact-email'));
        const category = document.createElement('td');
        category.append(textElement('span', customerTypes[customer.customerType], `badge ${customer.customerType}`));
        const actions = document.createElement('td');
        const actionGroup = textElement('div', '', 'row-actions');
        const edit = textElement('button', 'Chỉnh sửa', 'row-button');
        edit.type = 'button';
        edit.setAttribute('aria-label', `Chỉnh sửa ${customer.fullName}`);
        edit.addEventListener('click', () => openCustomerForm(customer));
        const quote = textElement('button', 'Tính giá', 'row-button');
        quote.type = 'button';
        quote.setAttribute('aria-label', `Tính giá vé cho ${customer.fullName}`);
        quote.addEventListener('click', () => openPriceForm(customer));
        actionGroup.append(edit, quote);
        actions.append(actionGroup);
        row.append(identity, contact, category, textElement('td', customer.studentId || '—'),
            textElement('td', percentFormatter.format(customer.discountRate), 'discount'), actions);
        fragment.append(row);
    }
    elements.customerTableBody.replaceChildren(fragment);
    elements.tableState.hidden = filtered.length > 0;
    elements.tableState.textContent = customers.length === 0
        ? 'Chưa có khách hàng. Đăng ký khách hàng đầu tiên để bắt đầu.'
        : 'Không tìm thấy khách hàng phù hợp. Thử thay đổi từ khóa hoặc bộ lọc.';
    elements.resultCount.textContent = `Hiển thị ${filtered.length} / ${customers.length} khách hàng`;
}

function textElement(tag, text, className = '') {
    const element = document.createElement(tag);
    element.textContent = text;
    element.className = className;
    return element;
}

function showNotice(message, isError = false) {
    elements.notice.textContent = message;
    elements.notice.className = `notice${isError ? ' error' : ''}`;
    elements.notice.hidden = false;
}

function openCustomerForm(customer = null) {
    editingCustomerId = customer?.id || null;
    elements.customerForm.reset();
    elements.formError.hidden = true;
    customerFields.forEach(clearFieldError);
    invalidateStudentCheck();
    elements.customerDialogTitle.textContent = customer ? 'Chỉnh sửa khách hàng' : 'Đăng ký khách hàng';
    elements.saveCustomer.textContent = customer ? 'Lưu thay đổi' : 'Đăng ký khách hàng';
    elements.customerReference.hidden = !customer;
    elements.customerReference.textContent = customer ? `Mã khách hàng: ${customer.id}` : '';
    if (customer) customerFields.forEach(field => { elements[field].value = customer[field] || ''; });
    updateCustomerType();
    elements.customerDialog.showModal();
    elements.fullName.focus();
}

function closeCustomerForm() {
    if (!savingCustomer) elements.customerDialog.close();
}

function updateCustomerType() {
    const type = elements.customerType.value;
    const isStudent = type === 'STUDENT';
    elements.studentFields.hidden = !isStudent;
    elements.studentId.required = isStudent;
    elements.studentId.disabled = !isStudent;
    if (!isStudent) elements.studentId.value = '';
    invalidateStudentCheck();
    clearFieldError('studentId');
    elements.formBenefitTitle.textContent = `Chính sách ${customerTypes[type].toLowerCase()}`;
    const rate = discountPolicies?.[type];
    elements.formBenefitDescription.textContent = rate > 0
        ? `Giảm ${percentFormatter.format(rate)} trên tổng giá vé cơ bản và phụ phí ghế.`
        : 'Áp dụng giá vé tiêu chuẩn, chưa có ưu đãi theo loại khách.';
}

function clearFieldError(field) {
    elements[`${field}Error`].textContent = '';
    elements[field].removeAttribute('aria-invalid');
}

function showFieldErrors(errors = {}) {
    for (const [field, message] of Object.entries(errors)) {
        if (!customerFields.includes(field)) continue;
        elements[`${field}Error`].textContent = message;
        elements[field].setAttribute('aria-invalid', 'true');
    }
    const firstInvalid = customerFields.find(field => errors[field]);
    if (firstInvalid) elements[firstInvalid].focus();
}

async function saveCustomer(event) {
    event.preventDefault();
    if (savingCustomer) return;
    elements.fullName.value = elements.fullName.value.trim();
    elements.email.value = elements.email.value.trim();
    elements.phone.value = elements.phone.value.trim();
    customerFields.forEach(clearFieldError);
    elements.formError.hidden = true;
    if (!elements.customerForm.reportValidity()) return;
    const payload = Object.fromEntries(customerFields.map(field => [field, elements[field].value]));
    payload.studentId = payload.customerType === 'STUDENT' ? payload.studentId.trim().toUpperCase() : null;
    savingCustomer = true;
    elements.customerFields.disabled = true;
    elements.saveCustomer.disabled = true;
    elements.cancelCustomer.disabled = true;
    elements.closeCustomerDialog.disabled = true;
    const buttonText = elements.saveCustomer.textContent;
    elements.saveCustomer.textContent = 'Đang lưu…';
    let validationErrors = null;
    try {
        const customer = await requestApi(editingCustomerId ? `/${encodeURIComponent(editingCustomerId)}` : '', {
            method: editingCustomerId ? 'PUT' : 'POST', body: JSON.stringify(payload)
        });
        loadVersion++;
        elements.refreshCustomers.disabled = false;
        elements.addCustomer.disabled = false;
        const index = customers.findIndex(existing => existing.id === customer.id);
        if (index >= 0) customers[index] = customer;
        else customers.push(customer);
        renderCustomers();
        updateStatistics();
        elements.customerDialog.close();
        showNotice(`${editingCustomerId ? 'Đã cập nhật' : 'Đã đăng ký'} khách hàng ${customer.fullName}.`);
    } catch (error) {
        elements.formError.textContent = error.message;
        elements.formError.hidden = false;
        validationErrors = error.fieldErrors;
    } finally {
        savingCustomer = false;
        elements.customerFields.disabled = false;
        elements.saveCustomer.disabled = false;
        elements.cancelCustomer.disabled = false;
        elements.closeCustomerDialog.disabled = false;
        elements.saveCustomer.textContent = buttonText;
        if (validationErrors) showFieldErrors(validationErrors);
    }
}

function invalidateStudentCheck() {
    studentCheckVersion++;
    elements.studentCheckResult.hidden = true;
    elements.checkStudentId.disabled = false;
    elements.checkStudentId.textContent = 'Kiểm tra mã';
}

async function checkStudentId() {
    const studentId = elements.studentId.value.trim().toUpperCase();
    if (!studentId) {
        showFieldErrors({ studentId: 'Vui lòng nhập mã sinh viên trước khi kiểm tra.' });
        return;
    }
    const version = ++studentCheckVersion;
    const params = new URLSearchParams({ studentId });
    if (editingCustomerId) params.set('excludeCustomerId', editingCustomerId);
    elements.checkStudentId.disabled = true;
    elements.checkStudentId.textContent = 'Đang kiểm tra…';
    try {
        const result = await requestApi(`/student-id/check?${params}`);
        if (version !== studentCheckVersion) return;
        elements.studentCheckResult.textContent = result.message;
        elements.studentCheckResult.className = `check-result${result.valid && result.available ? '' : ' error'}`;
        elements.studentCheckResult.hidden = false;
    } catch (error) {
        if (version !== studentCheckVersion) return;
        elements.studentCheckResult.textContent = error.message;
        elements.studentCheckResult.className = 'check-result error';
        elements.studentCheckResult.hidden = false;
    } finally {
        if (version === studentCheckVersion) {
            elements.checkStudentId.disabled = false;
            elements.checkStudentId.textContent = 'Kiểm tra mã';
        }
    }
}

function openPriceForm(customer) {
    quotedCustomerId = customer.id;
    elements.priceForm.reset();
    invalidateQuote();
    elements.priceCustomer.textContent = `${customer.fullName} · ${customerTypes[customer.customerType]} · Ưu đãi ${percentFormatter.format(customer.discountRate)}`;
    elements.priceDialog.showModal();
}

function invalidateQuote() {
    quoteVersion++;
    elements.priceResult.hidden = true;
    elements.priceError.hidden = true;
    elements.calculatePrice.disabled = false;
    elements.calculatePrice.textContent = 'Tính giá ưu đãi';
}

async function calculatePrice(event) {
    event.preventDefault();
    if (!elements.priceForm.reportValidity()) return;
    const version = ++quoteVersion;
    elements.priceError.hidden = true;
    elements.priceResult.hidden = true;
    elements.calculatePrice.disabled = true;
    elements.calculatePrice.textContent = 'Đang tính…';
    try {
        const quote = await requestApi(`/${encodeURIComponent(quotedCustomerId)}/price-quote`, {
            method: 'POST',
            body: JSON.stringify({ basePrice: elements.basePrice.value, seatSurcharge: elements.seatSurcharge.value })
        });
        if (version !== quoteVersion) return;
        elements.quoteSubtotal.textContent = moneyFormatter.format(quote.subtotal);
        elements.quoteDiscountLabel.textContent = `Ưu đãi (${percentFormatter.format(quote.discountRate)})`;
        elements.quoteDiscount.textContent = `−${moneyFormatter.format(quote.discountAmount)}`;
        elements.quoteFinalPrice.textContent = moneyFormatter.format(quote.finalPrice);
        elements.priceResult.hidden = false;
    } catch (error) {
        if (version !== quoteVersion) return;
        const details = Object.values(error.fieldErrors || {}).join(' ');
        elements.priceError.textContent = details || error.message;
        elements.priceError.hidden = false;
    } finally {
        if (version === quoteVersion) {
            elements.calculatePrice.disabled = false;
            elements.calculatePrice.textContent = 'Tính giá ưu đãi';
        }
    }
}
