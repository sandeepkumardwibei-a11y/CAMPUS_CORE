// CampusCore Frontend Client Engine
// Single Page Application state management and API bindings

const API_BASE = '';

let state = {
    token: localStorage.getItem('jwtToken'),
    user: JSON.parse(localStorage.getItem('currentUser') || 'null'),
    notifications: []
};

// --- Page Load Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    initApp();
    if (state.token && state.user) {
        showAppScreen();
    } else {
        showAuthScreen();
    }
});

function initApp() {
    lucide.createIcons();
    toggleRegDetails();
}

// --- Auth Toggle Visual UI helper ---
function switchAuthTab(tab) {
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const tabLogin = document.getElementById('tab-login');
    const tabRegister = document.getElementById('tab-register');

    if (tab === 'login') {
        loginForm.style.display = 'block';
        registerForm.style.display = 'none';
        tabLogin.classList.add('active');
        tabRegister.classList.remove('active');
    } else {
        loginForm.style.display = 'none';
        registerForm.style.display = 'block';
        tabLogin.classList.remove('active');
        tabRegister.classList.add('active');
        loadRegistrationDropdowns();
    }
}

function toggleRegDetails() {
    const roleSelect = document.getElementById('reg-role');
    const deptGroup = document.getElementById('reg-dept-group');
    if (roleSelect.value === 'STUDENT' || roleSelect.value === 'FACULTY') {
        deptGroup.style.display = 'block';
    } else {
        deptGroup.style.display = 'none';
    }
}

// --- API Request Wrapper (Bearer Token & Error Handling) ---
async function apiFetch(url, options = {}) {
    options.headers = options.headers || {};
    
    // Add JWT Token if authenticated
    if (state.token) {
        options.headers['Authorization'] = `Bearer ${state.token}`;
    }
    
    // Set content type for JSON payloads
    if (options.body && typeof options.body === 'object') {
        options.body = JSON.stringify(options.body);
        options.headers['Content-Type'] = 'application/json';
    }

    try {
        const response = await fetch(`${API_BASE}${url}`, options);
        
        // Handle Token Expiry / Unauthorized
        if (response.status === 401) {
            handleLogout();
            showToast('Session expired. Please sign in again.', 'warning');
            throw new Error('Unauthorized');
        }
        
        // Handle Access Denied
        if (response.status === 403) {
            showToast('Access denied: Insufficient privileges.', 'danger');
            throw new Error('Forbidden');
        }

        const data = await response.json();
        
        if (!response.ok) {
            const errMsg = data.message || `Request failed with status ${response.status}`;
            showToast(errMsg, 'danger');
            throw new Error(errMsg);
        }

        return data; // CampusCore standard ApiResponse wrapper
    } catch (error) {
        console.error('API Fetch error:', error);
        throw error;
    }
}

// --- Toast System ---
function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast glass-panel`;
    
    let icon = 'check-circle';
    let color = 'var(--success)';
    if (type === 'danger') { icon = 'alert-triangle'; color = 'var(--danger)'; }
    if (type === 'warning') { icon = 'alert-circle'; color = 'var(--warning)'; }
    if (type === 'info') { icon = 'info'; color = 'var(--secondary)'; }

    toast.innerHTML = `
        <i data-lucide="${icon}" style="color: ${color}"></i>
        <div style="flex: 1; font-size: 13px;">${message}</div>
    `;
    
    container.appendChild(toast);
    lucide.createIcons();
    
    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s ease reverse';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// --- Auth Submit Handlers ---
async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;

    try {
        const res = await apiFetch('/auth/login', {
            method: 'POST',
            body: { email, password }
        });
        
        state.token = res.data.token;
        state.user = res.data.user;
        
        localStorage.setItem('jwtToken', state.token);
        localStorage.setItem('currentUser', JSON.stringify(state.user));
        
        showToast('Login successful! Welcome to CampusCore.', 'success');
        showAppScreen();
    } catch (err) {
        // Error toast already displayed in apiFetch wrapper
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const name = document.getElementById('reg-name').value;
    const email = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;
    const phone = document.getElementById('reg-phone').value;
    const role = document.getElementById('reg-role').value;
    const programId = document.getElementById('reg-program').value || null;

    try {
        const res = await apiFetch('/auth/register', {
            method: 'POST',
            body: {
                name, email, password, phone, role,
                departmentId: programId ? parseInt(programId) : null
            }
        });
        
        showToast('Account registered successfully! Please sign in.', 'success');
        switchAuthTab('login');
    } catch (err) {
        // Handled
    }
}

function handleLogout() {
    state.token = null;
    state.user = null;
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('currentUser');
    showAuthScreen();
}

// --- Screen State Control ---
function showAuthScreen() {
    document.getElementById('auth-screen').style.display = 'flex';
    document.getElementById('app-screen').style.display = 'none';
}

function showAppScreen() {
    document.getElementById('auth-screen').style.display = 'none';
    document.getElementById('app-screen').style.display = 'flex';
    
    // Set Profile Info
    document.getElementById('user-display-name').textContent = state.user.name;
    document.getElementById('user-display-role').textContent = state.user.role.replace('_', ' ');
    document.getElementById('user-avatar').textContent = state.user.name.split(' ').map(n=>n[0]).join('').substring(0,2).toUpperCase();
    
    renderSidebarMenu();
    loadUnreadNotificationsCount();
    navigateTo('dashboard');
}

// --- Sidebar Menu Customizer per Role ---
function renderSidebarMenu() {
    const menuNav = document.getElementById('sidebar-menu');
    const role = state.user.role;
    let menuHtml = `
        <a class="menu-item active" onclick="navigateTo('dashboard')" id="menu-dashboard">
            <i data-lucide="layout-dashboard"></i>
            <span>Dashboard</span>
        </a>
    `;

    if (role === 'APPLICANT') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('applicant-apply')" id="menu-applicant-apply">
                <i data-lucide="file-text"></i>
                <span>Submit Admission</span>
            </a>
            <a class="menu-item" onclick="navigateTo('applicant-track')" id="menu-applicant-track">
                <i data-lucide="compass"></i>
                <span>Track Status</span>
            </a>
        `;
    } else if (role === 'STUDENT') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('student-registration')" id="menu-student-registration">
                <i data-lucide="book-open"></i>
                <span>Enroll Courses</span>
            </a>
            <a class="menu-item" onclick="navigateTo('student-attendance')" id="menu-student-attendance">
                <i data-lucide="calendar"></i>
                <span>Attendance Tracker</span>
            </a>
            <a class="menu-item" onclick="navigateTo('student-grades')" id="menu-student-grades">
                <i data-lucide="award"></i>
                <span>Official Grades</span>
            </a>
            <a class="menu-item" onclick="navigateTo('student-fees')" id="menu-student-fees">
                <i data-lucide="credit-card"></i>
                <span>Fees & Invoices</span>
            </a>
            <a class="menu-item" onclick="navigateTo('student-booking')" id="menu-student-booking">
                <i data-lucide="map-pin"></i>
                <span>Facility Bookings</span>
            </a>
        `;
    } else if (role === 'FACULTY') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('faculty-attendance')" id="menu-faculty-attendance">
                <i data-lucide="check-square"></i>
                <span>Mark Attendance</span>
            </a>
            <a class="menu-item" onclick="navigateTo('faculty-grades')" id="menu-faculty-grades">
                <i data-lucide="edit-3"></i>
                <span>Post Grades</span>
            </a>
        `;
    } else if (role === 'EXAM_CONTROLLER') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('exam-schedule')" id="menu-exam-schedule">
                <i data-lucide="calendar"></i>
                <span>Schedule Exams</span>
            </a>
            <a class="menu-item" onclick="navigateTo('exam-compiler')" id="menu-exam-compiler">
                <i data-lucide="pie-chart"></i>
                <span>Result Compiler</span>
            </a>
        `;
    } else if (role === 'ACCOUNTS') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('accounts-ledger')" id="menu-accounts-ledger">
                <i data-lucide="dollar-sign"></i>
                <span>Accounts Ledger</span>
            </a>
        `;
    } else if (role === 'ADMIN') {
        menuHtml += `
            <a class="menu-item" onclick="navigateTo('admin-programs')" id="menu-admin-programs">
                <i data-lucide="package"></i>
                <span>Programs</span>
            </a>
            <a class="menu-item" onclick="navigateTo('admin-courses')" id="menu-admin-courses">
                <i data-lucide="book"></i>
                <span>Courses Catalog</span>
            </a>
            <a class="menu-item" onclick="navigateTo('admin-hostels')" id="menu-admin-hostels">
                <i data-lucide="home"></i>
                <span>Hostels</span>
            </a>
            <a class="menu-item" onclick="navigateTo('admin-bookings')" id="menu-admin-bookings">
                <i data-lucide="calendar"></i>
                <span>Facility Requests</span>
            </a>
            <a class="menu-item" onclick="navigateTo('admin-admissions')" id="menu-admin-admissions">
                <i data-lucide="users"></i>
                <span>Admissions</span>
            </a>
            <a class="menu-item" onclick="navigateTo('admin-users')" id="menu-admin-users">
                <i data-lucide="user-check"></i>
                <span>User Accounts</span>
            </a>
        `;
    }

    menuNav.innerHTML = menuHtml;
    lucide.createIcons();
}

// --- Navigation Controller ---
function navigateTo(pageId) {
    // Toggles active section
    document.querySelectorAll('.page-section').forEach(sec => sec.classList.remove('active'));
    const targetSection = document.getElementById(`page-${pageId}`);
    if (targetSection) targetSection.classList.add('active');

    // Highlight menu link
    document.querySelectorAll('.menu-item').forEach(link => link.classList.remove('active'));
    const currentLink = document.getElementById(`menu-${pageId}`);
    if (currentLink) currentLink.classList.add('active');

    // Update Header
    let displayTitle = pageId.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    if (pageId === 'dashboard') displayTitle = `${state.user.role.replace('_', ' ')} Portal Overview`;
    document.getElementById('current-page-title').textContent = displayTitle;

    // Load data for specific page
    loadPageData(pageId);
}

// --- Page Data Router ---
function loadPageData(pageId) {
    switch (pageId) {
        case 'dashboard':
            loadDashboardHome();
            break;
        case 'notifications':
            loadNotificationsCenter();
            break;
        case 'applicant-apply':
            loadApplicantApplyForm();
            break;
        case 'applicant-track':
            loadApplicantApplications();
            break;
        case 'student-registration':
            loadOfferedCourses();
            break;
        case 'student-attendance':
            loadStudentAttendance();
            break;
        case 'student-grades':
            loadStudentGrades();
            break;
        case 'student-fees':
            loadStudentFees();
            break;
        case 'student-booking':
            loadStudentBookings();
            break;
        case 'faculty-attendance':
            loadFacultyCoursesForAttendance();
            break;
        case 'faculty-grades':
            loadFacultyCoursesForGrades();
            break;
        case 'exam-schedule':
            loadScheduledExams();
            break;
        case 'exam-compiler':
            loadExamCompilerData();
            break;
        case 'accounts-ledger':
            loadAccountsLedger();
            break;
        case 'admin-programs':
            loadAdminPrograms();
            break;
        case 'admin-courses':
            loadAdminCourses();
            break;
        case 'admin-hostels':
            loadAdminHostels();
            break;
        case 'admin-bookings':
            loadAdminBookings();
            break;
        case 'admin-admissions':
            loadAdminAdmissions();
            break;
        case 'admin-users':
            loadAdminUsers();
            break;
    }
}

// --- Helper: Register Screen Dropdowns ---
async function loadRegistrationDropdowns() {
    const regProg = document.getElementById('reg-program');
    if (!regProg) return;
    try {
        const res = await fetch(`${API_BASE}/programs`);
        const json = await res.json();
        if (json.data && json.data.length > 0) {
            regProg.innerHTML = json.data.map(p => `
                <option value="${p.programId}">${p.programName} (${p.level})</option>
            `).join('');
        } else {
            regProg.innerHTML = `<option value="">No programs available</option>`;
        }
    } catch (err) {
        console.error('Failed to load programs for registration dropdown', err);
    }
}

// ==========================================
// MODULE: DASHBOARD HOME
// ==========================================
async function loadDashboardHome() {
    const welcome = document.getElementById('welcome-message');
    welcome.textContent = `Welcome back, ${state.user.name}!`;

    const metricsGrid = document.getElementById('dashboard-metrics');
    const summaryPanels = document.getElementById('dashboard-summary-panels');
    
    // Clear elements
    metricsGrid.innerHTML = '';
    summaryPanels.innerHTML = '';

    const role = state.user.role;

    if (role === 'APPLICANT') {
        // Load Application metrics
        try {
            const apps = await apiFetch(`/admissions/my-applications`);
            const subCount = apps.data.length;
            const enrolled = apps.data.some(a => a.status === 'ENROLLED');
            
            metricsGrid.innerHTML = `
                <div class="glass-card metric-card">
                    <div class="metric-info">
                        <p>Applications Submitted</p>
                        <h3>${subCount}</h3>
                    </div>
                    <div class="metric-icon" style="background: var(--primary-light);">
                        <i data-lucide="file" style="color: var(--primary)"></i>
                    </div>
                </div>
                <div class="glass-card metric-card">
                    <div class="metric-info">
                        <p>Enrolled Status</p>
                        <h3>${enrolled ? 'Enrolled' : 'Pending'}</h3>
                    </div>
                    <div class="metric-icon" style="background: var(--success-light);">
                        <i data-lucide="check" style="color: var(--success)"></i>
                    </div>
                </div>
            `;
            
            summaryPanels.innerHTML = `
                <div class="glass-card action-card">
                    <h3>Submit Admission Form</h3>
                    <p>Apply for Undergraduate (UG) or Postgraduate (PG) programs online through our paperless admission module.</p>
                    <button class="btn btn-primary" onclick="navigateTo('applicant-apply')">Apply Now</button>
                </div>
                <div class="glass-card action-card">
                    <h3>Track Status</h3>
                    <p>Review the real-time status of your documents, qualification verification, and view offer letters issued.</p>
                    <button class="btn btn-secondary" onclick="navigateTo('applicant-track')">View Status</button>
                </div>
            `;
        } catch (err) {}
    } 
    else if (role === 'STUDENT') {
        try {
            const courses = await apiFetch(`/registrations/student/${state.user.userId}`);
            const regCount = courses.data.length > 0 ? courses.data[0].courses.length : 0;
            const invoices = await apiFetch(`/fees/student/${state.user.userId}/invoices`);
            const unpaidCount = invoices.data.filter(i => i.status === 'PENDING').length;
            
            metricsGrid.innerHTML = `
                <div class="glass-card metric-card">
                    <div class="metric-info">
                        <p>Registered Courses</p>
                        <h3>${regCount}</h3>
                    </div>
                    <div class="metric-icon" style="background: var(--primary-light);">
                        <i data-lucide="book-open" style="color: var(--primary)"></i>
                    </div>
                </div>
                <div class="glass-card metric-card">
                    <div class="metric-info">
                        <p>Pending Invoices</p>
                        <h3>${unpaidCount}</h3>
                    </div>
                    <div class="metric-icon" style="background: var(--warning-light);">
                        <i data-lucide="credit-card" style="color: var(--warning)"></i>
                    </div>
                </div>
            `;

            summaryPanels.innerHTML = `
                <div class="glass-card action-card">
                    <h3>Quick Course Registration</h3>
                    <p>Enroll in the current semester's core classes, check required credits, and review syllabus catalogues.</p>
                    <button class="btn btn-primary" onclick="navigateTo('student-registration')">Register Now</button>
                </div>
                <div class="glass-card action-card">
                    <h3>Academic Performance</h3>
                    <p>Review current attendance logs and see published midterm / endterm course letter grades card.</p>
                    <button class="btn btn-secondary" onclick="navigateTo('student-grades')">View Results</button>
                </div>
            `;
        } catch (err) {}
    }
    else if (role === 'FACULTY') {
        try {
            const res = await apiFetch(`/courses/faculty/${state.user.userId}`);
            const coursesCount = res.data.length;
            
            metricsGrid.innerHTML = `
                <div class="glass-card metric-card">
                    <div class="metric-info">
                        <p>Courses Instructing</p>
                        <h3>${coursesCount}</h3>
                    </div>
                    <div class="metric-icon" style="background: var(--primary-light);">
                        <i data-lucide="book" style="color: var(--primary)"></i>
                    </div>
                </div>
            `;

            summaryPanels.innerHTML = `
                <div class="glass-card action-card">
                    <h3>Mark Attendance</h3>
                    <p>Select student roster sheet, fill daily student attendance states (Present, Absent, Late) and submit sheets.</p>
                    <button class="btn btn-primary" onclick="navigateTo('faculty-attendance')">Go to Roster</button>
                </div>
                <div class="glass-card action-card">
                    <h3>Publish Grades</h3>
                    <p>Post midterm / endterm exam performance marks, edit letter grade cards for course students.</p>
                    <button class="btn btn-secondary" onclick="navigateTo('faculty-grades')">Post Grades</button>
                </div>
            `;
        } catch (err) {}
    }
    else {
        // ADMIN / ACCOUNTS / EXAM_CONTROLLER
        metricsGrid.innerHTML = `
            <div class="glass-card metric-card">
                <div class="metric-info">
                    <p>User Accounts</p>
                    <h3>Manage</h3>
                </div>
                <div class="metric-icon" style="background: var(--primary-light);">
                    <i data-lucide="users" style="color: var(--primary)"></i>
                </div>
            </div>
            <div class="glass-card metric-card">
                <div class="metric-info">
                    <p>System Logs</p>
                    <h3>Active</h3>
                </div>
                <div class="metric-icon" style="background: var(--secondary-light);">
                    <i data-lucide="activity" style="color: var(--secondary)"></i>
                </div>
            </div>
        `;

        summaryPanels.innerHTML = `
            <div class="glass-card action-card">
                <h3>Campus Resource Settings</h3>
                <p>Configure departments programs, seed course database registries, and manage hostel room allotments.</p>
                <button class="btn btn-primary" onclick="navigateTo('admin-programs')">Settings Panel</button>
            </div>
            <div class="glass-card action-card">
                <h3>System Notifications</h3>
                <p>Dispatch general announcements or specific notices to students and faculty members.</p>
                <button class="btn btn-secondary" onclick="navigateTo('notifications')">Notifications</button>
            </div>
        `;
    }

    lucide.createIcons();
}

// ==========================================
// MODULE: NOTIFICATIONS CENTER
// ==========================================
async function loadUnreadNotificationsCount() {
    if (!state.token || !state.user) return;
    try {
        const res = await apiFetch(`/notifications/user/${state.user.userId}/unread-count`);
        const count = res.data;
        const badge = document.getElementById('unread-notifications-count');
        if (count > 0) {
            badge.textContent = count;
            badge.style.display = 'flex';
        } else {
            badge.style.display = 'none';
        }
    } catch (err) {}
}

async function loadNotificationsCenter() {
    const listContainer = document.getElementById('notifications-list');
    try {
        const res = await apiFetch(`/notifications/user/${state.user.userId}`);
        const notifs = res.data.content;
        
        if (notifs.length === 0) {
            listContainer.innerHTML = `<p style="color: var(--text-muted); text-align: center;">No notifications found.</p>`;
            return;
        }

        listContainer.innerHTML = notifs.map(n => `
            <div class="glass-card" style="display: flex; justify-content: space-between; align-items: center; ${n.read ? 'opacity: 0.7' : 'border-left: 4px solid var(--primary)'}">
                <div>
                    <span class="badge ${n.category === 'FINANCE' ? 'badge-danger' : n.category === 'ACADEMIC' ? 'badge-info' : 'badge-success'}" style="margin-bottom: 6px;">
                        ${n.category}
                    </span>
                    <p style="font-size: 14px;">${n.message}</p>
                    <small style="color: var(--text-muted)">Date: ${n.sentTime ? n.sentTime.substring(0, 10) : ''}</small>
                </div>
                ${!n.read ? `<button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="markNotificationRead(${n.notificationId})">Read</button>` : ''}
            </div>
        `).join('');
    } catch (err) {}
}

async function markNotificationRead(id) {
    try {
        await apiFetch(`/notifications/${id}/read`, { method: 'PUT' });
        showToast('Notification marked as read.');
        loadUnreadNotificationsCount();
        loadNotificationsCenter();
    } catch (err) {}
}

async function markAllNotificationsRead() {
    try {
        await apiFetch(`/notifications/user/${state.user.userId}/read-all`, { method: 'PUT' });
        showToast('All notifications marked as read.');
        loadUnreadNotificationsCount();
        loadNotificationsCenter();
    } catch (err) {}
}

function toggleNotificationsPanel() {
    navigateTo('notifications');
}

// ==========================================
// MODULE: APPLICANT PORTAL
// ==========================================
async function loadApplicantApplyForm() {
    document.getElementById('apply-name').value = state.user.name;
    document.getElementById('apply-email').value = state.user.email;
    document.getElementById('apply-phone').value = state.user.phone || '';

    const programSelect = document.getElementById('apply-program');
    try {
        const res = await apiFetch(`/programs`);
        programSelect.innerHTML = res.data.map(p => `
            <option value="${p.programId}">${p.programName} (${p.level})</option>
        `).join('');
    } catch (err) {}
}

async function submitAdmissionApplication(e) {
    e.preventDefault();
    const phone = document.getElementById('apply-phone').value;
    const academicYear = document.getElementById('apply-year').value;
    const programId = document.getElementById('apply-program').value;
    const qualifyingScore = parseFloat(document.getElementById('apply-score').value);

    try {
        await apiFetch(`/admissions/apply`, {
            method: 'POST',
            body: {
                applicantName: state.user.name,
                email: state.user.email,
                phone,
                programId: parseInt(programId),
                academicYear,
                qualifyingScore
            }
        });
        showToast('Admission application submitted successfully!');
        navigateTo('applicant-track');
    } catch (err) {}
}

async function loadApplicantApplications() {
    const tbody = document.getElementById('applicant-track-tbody');
    try {
        const res = await apiFetch(`/admissions/my-applications`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No applications submitted yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(a => {
            let badgeClass = 'badge-warning';
            if (a.status === 'ENROLLED') badgeClass = 'badge-success';
            if (a.status === 'REJECTED' || a.status === 'WITHDRAWN') badgeClass = 'badge-danger';
            if (a.status === 'OFFER_ISSUED') badgeClass = 'badge-info';

            let actionHtml = '';
            if (a.status === 'OFFER_ISSUED') {
                actionHtml = `
                    <button class="btn btn-success" style="padding: 6px 12px; font-size: 12px;" onclick="acceptApplicantOffer(${a.applicationId})">Accept</button>
                    <button class="btn btn-danger" style="padding: 6px 12px; font-size: 12px;" onclick="withdrawApplicantApplication(${a.applicationId})">Decline</button>
                `;
            } else if (a.status === 'SUBMITTED' || a.status === 'SHORTLISTED') {
                actionHtml = `
                    <button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="withdrawApplicantApplication(${a.applicationId})">Withdraw</button>
                `;
            } else {
                actionHtml = `<span style="color: var(--text-muted)">None</span>`;
            }

            return `
                <tr>
                    <td>${a.applicationId}</td>
                    <td>${a.programName}</td>
                    <td>${a.academicYear}</td>
                    <td>${a.qualifyingScore}%</td>
                    <td>${a.applicationDate}</td>
                    <td><span class="badge ${badgeClass}">${a.status}</span></td>
                    <td>${actionHtml}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function acceptApplicantOffer(appId) {
    try {
        await apiFetch(`/admissions/${appId}/accept`, { method: 'PUT' });
        showToast('Congratulations! Offer accepted, you are enrolled.');
        loadApplicantApplications();
    } catch (err) {}
}

async function withdrawApplicantApplication(appId) {
    if (!confirm('Are you sure you want to withdraw this application?')) return;
    try {
        await apiFetch(`/admissions/${appId}/withdraw`, { method: 'PUT' });
        showToast('Application withdrawn.');
        loadApplicantApplications();
    } catch (err) {}
}

// ==========================================
// MODULE: STUDENT PORTAL
// ==========================================
async function loadOfferedCourses() {
    const tbody = document.getElementById('student-courses-tbody');
    const sem = document.getElementById('stud-reg-semester').value;
    
    // We get student's programId. To simplify, we pull current user details
    // If user's departmentId is programId, we use it. Otherwise, default 1.
    const programId = state.user.departmentId || 1;

    try {
        const res = await apiFetch(`/courses/program/${programId}?semester=${sem}`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted)">No courses offered for semester ${sem}.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(c => `
            <tr>
                <td>
                    <div class="custom-checkbox" id="chk-course-${c.courseId}" onclick="toggleCourseCheckbox(this, ${c.courseId})">
                        <i data-lucide="check" style="width: 14px; display: none;"></i>
                    </div>
                </td>
                <td>${c.courseCode}</td>
                <td>${c.courseName}</td>
                <td>${c.credits}</td>
                <td>${c.facultyName || 'TBA'}</td>
                <td>—</td>
                <td><span class="badge badge-success">${c.status}</span></td>
            </tr>
        `).join('');
        lucide.createIcons();
    } catch (err) {}
}

let selectedCourseIds = [];
function toggleCourseCheckbox(el, courseId) {
    const icon = el.querySelector('i');
    if (el.classList.contains('checked')) {
        el.classList.remove('checked');
        icon.style.display = 'none';
        selectedCourseIds = selectedCourseIds.filter(id => id !== courseId);
    } else {
        el.classList.add('checked');
        icon.style.display = 'block';
        selectedCourseIds.push(courseId);
    }
}

async function submitSemesterRegistration() {
    if (selectedCourseIds.length === 0) {
        showToast('Please select at least one course to register.', 'warning');
        return;
    }

    const sem = document.getElementById('stud-reg-semester').value;
    const year = document.getElementById('stud-reg-year').value;
    const programId = state.user.departmentId || 1;

    try {
        await apiFetch('/registrations', {
            method: 'POST',
            body: {
                studentId: state.user.userId,
                programId: programId,
                academicYear: year,
                semester: parseInt(sem),
                courseIds: selectedCourseIds
            }
        });
        showToast('Semester registration submitted successfully! Awaiting confirmation.');
        selectedCourseIds = [];
        loadOfferedCourses();
    } catch (err) {}
}

async function loadStudentAttendance() {
    const tbody = document.getElementById('student-attendance-tbody');
    try {
        const res = await apiFetch(`/attendance/student/${state.user.userId}?academicYear=2026-2027`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted)">No attendance summaries found for academic year 2026-2027.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(a => {
            const alertClass = a.shortageFlag ? 'badge-danger' : 'badge-success';
            const progressColor = a.shortageFlag ? 'var(--danger)' : 'var(--success)';

            return `
                <tr>
                    <td>${a.courseId} (ID)</td>
                    <td>${a.courseName}</td>
                    <td>${a.totalLectures}</td>
                    <td>${a.attendedLectures}</td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <span>${a.attendancePercent}%</span>
                            <div class="progress-container" style="flex: 1; max-width: 120px;">
                                <div class="progress-bar" style="width: ${a.attendancePercent}%; background: ${progressColor}"></div>
                            </div>
                        </div>
                    </td>
                    <td>
                        <span class="badge ${alertClass}">${a.shortageFlag ? 'Shortage Alert' : 'Good Standing'}</span>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function loadStudentGrades() {
    const gradeTbody = document.getElementById('student-grades-tbody');
    const resultTbody = document.getElementById('student-results-tbody');
    const gpaDisplay = document.getElementById('student-gpa-display');

    try {
        // Load grades
        const gradeRes = await apiFetch(`/exams/student/${state.user.userId}/grades`);
        if (gradeRes.data.length === 0) {
            gradeTbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted)">No published grades found.</td></tr>`;
        } else {
            gradeTbody.innerHTML = gradeRes.data.map(g => `
                <tr>
                    <td>${g.courseCode}</td>
                    <td>${g.courseName}</td>
                    <td>${g.marksObtained}</td>
                    <td>${g.maxMarks}</td>
                    <td style="font-weight: 700; color: var(--secondary)">${g.grade}</td>
                    <td><span class="badge badge-success">${g.status}</span></td>
                </tr>
            `).join('');
        }

        // Load compiled results
        const resultRes = await apiFetch(`/exams/student/${state.user.userId}/results`);
        if (resultRes.data.length === 0) {
            resultTbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted)">No compiled semester result cards found.</td></tr>`;
            gpaDisplay.textContent = 'CGPA: -- / SGPA: --';
        } else {
            resultTbody.innerHTML = resultRes.data.map(r => `
                <tr>
                    <td>${r.academicYear}</td>
                    <td>Semester ${r.semester}</td>
                    <td style="font-weight: 600;">${r.sgpa}</td>
                    <td style="font-weight: 600;">${r.cgpa}</td>
                    <td>${r.backlogs}</td>
                    <td><span class="badge badge-success">${r.status}</span></td>
                </tr>
            `).join('');

            // Highlight latest CGPA
            const latest = resultRes.data[resultRes.data.length - 1];
            gpaDisplay.textContent = `Latest CGPA: ${latest.cgpa} / Latest SGPA: ${latest.sgpa}`;
        }
    } catch (err) {}
}

async function loadStudentFees() {
    const tbody = document.getElementById('student-fees-tbody');
    try {
        const res = await apiFetch(`/fees/student/${state.user.userId}/invoices`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--text-muted);">No semester invoices issued.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(i => {
            const isPaid = i.status === 'PAID';
            return `
                <tr>
                    <td>#INV-${i.invoiceId}</td>
                    <td>${i.academicYear}</td>
                    <td>Semester ${i.semester}</td>
                    <td>$${i.tuitionFee}</td>
                    <td>$${i.hostelFee}</td>
                    <td style="font-weight: 700;">$${i.netPayable}</td>
                    <td>${i.dueDate}</td>
                    <td><span class="badge ${isPaid ? 'badge-success' : 'badge-warning'}">${i.status}</span></td>
                    <td>
                        ${isPaid ? `<span style="color: var(--text-muted)">Receipt Generated</span>` :
                        `<button class="btn btn-primary" style="padding: 6px 12px; font-size: 12px;" onclick="openPaymentModal(${i.invoiceId}, ${i.netPayable})">Pay Now</button>`}
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

// Payment Modal Handles
function openPaymentModal(invoiceId, amount) {
    document.getElementById('pay-invoice-id').value = invoiceId;
    document.getElementById('pay-invoice-amount').value = amount;
    document.getElementById('pay-invoice-ref').value = 'TXN' + Math.floor(Math.random() * 1000000000);
    document.getElementById('payment-modal').classList.add('active');
}

function closePaymentModal() {
    document.getElementById('payment-modal').classList.remove('active');
}

async function submitInvoicePayment(e) {
    e.preventDefault();
    const invoiceId = document.getElementById('pay-invoice-id').value;
    const paidAmount = parseFloat(document.getElementById('pay-invoice-amount').value);
    const mode = document.getElementById('pay-invoice-mode').value;
    const referenceNo = document.getElementById('pay-invoice-ref').value;

    try {
        await apiFetch(`/fees/payments`, {
            method: 'POST',
            body: {
                invoiceId: parseInt(invoiceId),
                paidAmount, mode, referenceNo
            }
        });
        showToast('Payment successful!');
        closePaymentModal();
        loadStudentFees();
    } catch (err) {}
}

async function requestFacilityBooking(e) {
    e.preventDefault();
    const facilityName = document.getElementById('book-facility-name').value;
    const bookingDate = document.getElementById('book-date').value;
    const startTime = document.getElementById('book-start').value + ':00';
    const endTime = document.getElementById('book-end').value + ':00';
    const purpose = document.getElementById('book-purpose').value;

    try {
        await apiFetch(`/bookings?userId=${state.user.userId}`, {
            method: 'POST',
            body: { facilityName, bookingDate, startTime, endTime, purpose }
        });
        showToast('Booking requested successfully!');
        document.getElementById('facility-booking-form').reset();
        loadStudentBookings();
    } catch (err) {}
}

async function loadStudentBookings() {
    const tbody = document.getElementById('student-bookings-tbody');
    try {
        const res = await apiFetch(`/bookings/user/${state.user.userId}`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted)">No booking requests.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(b => {
            let badgeClass = 'badge-warning';
            if (b.status === 'APPROVED') badgeClass = 'badge-success';
            if (b.status === 'REJECTED') badgeClass = 'badge-danger';

            return `
                <tr>
                    <td>${b.facilityName}</td>
                    <td>${b.bookingDate} (${b.startTime.substring(0,5)} - ${b.endTime.substring(0,5)})</td>
                    <td>${b.purpose}</td>
                    <td><span class="badge ${badgeClass}">${b.status}</span></td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

// ==========================================
// MODULE: FACULTY PORTAL
// ==========================================
async function loadFacultyCoursesForAttendance() {
    const select = document.getElementById('faculty-attendance-course');
    document.getElementById('faculty-attendance-date').value = new Date().toISOString().substring(0,10);

    try {
        const res = await apiFetch(`/courses/faculty/${state.user.userId}`);
        if (res.data.length === 0) {
            select.innerHTML = `<option value="">No courses assigned</option>`;
            return;
        }

        select.innerHTML = res.data.map(c => `
            <option value="${c.courseId}">${c.courseCode} - ${c.courseName}</option>
        `).join('');

        loadCourseStudentsForAttendance();
    } catch (err) {}
}

async function loadCourseStudentsForAttendance() {
    const courseId = document.getElementById('faculty-attendance-course').value;
    const sheet = document.getElementById('faculty-attendance-sheet');

    if (!courseId) {
        sheet.innerHTML = `<p style="color: var(--text-muted); text-align: center;">Please select a course to load the enrollment sheet.</p>`;
        return;
    }

    try {
        const res = await apiFetch(`/registrations/course/${courseId}`);
        if (res.data.length === 0) {
            sheet.innerHTML = `<p style="color: var(--text-muted); text-align: center;">No students enrolled in this course.</p>`;
            return;
        }

        sheet.innerHTML = res.data.map(r => `
            <div class="attendance-row" id="att-row-${r.studentId}">
                <span>${r.studentName} (ID: ${r.studentId})</span>
                <div class="attendance-status-selector" data-student-id="${r.studentId}">
                    <span class="status-pill active present" onclick="selectStatusPill(this, 'PRESENT')">Present</span>
                    <span class="status-pill absent" onclick="selectStatusPill(this, 'ABSENT')">Absent</span>
                    <span class="status-pill late" onclick="selectStatusPill(this, 'LATE')">Late</span>
                    <span class="status-pill duty" onclick="selectStatusPill(this, 'OFFICIAL_DUTY')">Duty Leave</span>
                </div>
            </div>
        `).join('');
    } catch (err) {}
}

function selectStatusPill(pill, status) {
    const parent = pill.parentElement;
    parent.querySelectorAll('.status-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
}

async function submitFacultyAttendance() {
    const courseId = document.getElementById('faculty-attendance-course').value;
    const lectureDate = document.getElementById('faculty-attendance-date').value;

    if (!courseId || !lectureDate) {
        showToast('Please specify course and lecture date.', 'warning');
        return;
    }

    const records = [];
    document.querySelectorAll('.attendance-status-selector').forEach(sel => {
        const studentId = parseInt(sel.getAttribute('data-student-id'));
        const activePill = sel.querySelector('.status-pill.active');
        let status = 'PRESENT';
        if (activePill.classList.contains('absent')) status = 'ABSENT';
        else if (activePill.classList.contains('late')) status = 'LATE';
        else if (activePill.classList.contains('duty')) status = 'OFFICIAL_DUTY';

        records.push({ studentId, status });
    });

    try {
        await apiFetch(`/attendance/mark`, {
            method: 'POST',
            body: {
                courseId: parseInt(courseId),
                lectureDate,
                records
            }
        });
        showToast('Attendance recorded successfully!');
    } catch (err) {}
}

async function loadFacultyCoursesForGrades() {
    const select = document.getElementById('faculty-grades-course');
    try {
        const res = await apiFetch(`/courses/faculty/${state.user.userId}`);
        if (res.data.length === 0) {
            select.innerHTML = `<option value="">No courses assigned</option>`;
            return;
        }

        select.innerHTML = res.data.map(c => `
            <option value="${c.courseId}">${c.courseCode} - ${c.courseName}</option>
        `).join('');

        loadCourseExams();
    } catch (err) {}
}

async function loadCourseExams() {
    const courseId = document.getElementById('faculty-grades-course').value;
    const select = document.getElementById('faculty-grades-exam');

    if (!courseId) return;

    try {
        const res = await apiFetch(`/exams/course/${courseId}?academicYear=2026-2027`);
        if (res.data.length === 0) {
            select.innerHTML = `<option value="">No exams scheduled</option>`;
            document.getElementById('faculty-grades-tbody').innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted)">Please schedule an exam first.</td></tr>`;
            return;
        }

        select.innerHTML = res.data.map(e => `
            <option value="${e.examId}">${e.examType} (${e.examDate})</option>
        `).join('');

        loadExamGradesSheet();
    } catch (err) {}
}

async function loadExamGradesSheet() {
    const examId = document.getElementById('faculty-grades-exam').value;
    const tbody = document.getElementById('faculty-grades-tbody');

    if (!examId) return;

    try {
        // Fetch registrations of course first
        const courseId = document.getElementById('faculty-grades-course').value;
        const regRes = await apiFetch(`/registrations/course/${courseId}`);

        // Fetch existing grades for the exam (if draft or filled)
        const gradesRes = await apiFetch(`/exams/${examId}/grades`);
        const gradeMap = {};
        gradesRes.data.forEach(g => {
            gradeMap[g.studentId] = g;
        });

        if (regRes.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted)">No students enrolled.</td></tr>`;
            return;
        }

        tbody.innerHTML = regRes.data.map(r => {
            const existing = gradeMap[r.studentId] || {};
            const marksVal = existing.marksObtained !== undefined ? existing.marksObtained : '';
            const statusLabel = existing.status || 'NEW / DRAFT';

            return `
                <tr class="grade-entry-row" data-student-id="${r.studentId}">
                    <td>${r.studentId}</td>
                    <td>${r.studentName}</td>
                    <td>
                        <input type="number" class="form-control std-marks-input" style="max-width: 120px;" value="${marksVal}" step="0.5" min="0" placeholder="Marks">
                    </td>
                    <td>100 (Max)</td>
                    <td><span class="badge badge-info">${statusLabel}</span></td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function submitFacultyGrades() {
    const examId = document.getElementById('faculty-grades-exam').value;
    if (!examId) return;

    const grades = [];
    document.querySelectorAll('.grade-entry-row').forEach(row => {
        const studentId = parseInt(row.getAttribute('data-student-id'));
        const inputVal = row.querySelector('.std-marks-input').value;
        if (inputVal !== '') {
            grades.push({
                studentId,
                marksObtained: parseFloat(inputVal)
            });
        }
    });

    if (grades.length === 0) {
        showToast('Please enter marks for at least one student.', 'warning');
        return;
    }

    try {
        await apiFetch(`/exams/${examId}/grades?facultyId=${state.user.userId}`, {
            method: 'POST',
            body: grades
        });
        showToast('Grades entries saved/updated successfully!');
        loadExamGradesSheet();
    } catch (err) {}
}

// ==========================================
// MODULE: EXAM CONTROLLER PORTAL
// ==========================================
async function loadScheduledExams() {
    const courseSelect = document.getElementById('schedule-exam-course');
    const registryTbody = document.getElementById('exam-registry-tbody');

    try {
        // Load course catalog options
        const courses = await apiFetch(`/courses`);
        courseSelect.innerHTML = courses.data.map(c => `
            <option value="${c.courseId}">${c.courseCode} - ${c.courseName}</option>
        `).join('');

        // Load exams scheduled
        const exams = await apiFetch(`/exams?academicYear=2026-2027&semester=3`);
        if (exams.data.content.length === 0) {
            registryTbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted)">No exams scheduled yet.</td></tr>`;
            return;
        }

        registryTbody.innerHTML = exams.data.content.map(e => `
            <tr>
                <td>${e.courseCode}</td>
                <td>
                    <strong>${e.examType}</strong><br>
                    <small>${e.examDate} at ${e.startTime.substring(0,5)} (${e.durationMins} mins) @ ${e.venue || 'Hall'}</small>
                </td>
                <td>${e.maxMarks}</td>
                <td>
                    ${e.status === 'PUBLISHED' ? `<span class="badge badge-success">Published</span>` :
                    `<button class="btn btn-success" style="padding: 6px 12px; font-size: 12px;" onclick="publishExamGrades(${e.examId})">Publish Grades</button>`}
                </td>
            </tr>
        `).join('');
    } catch (err) {}
}

async function handleScheduleExam(e) {
    e.preventDefault();
    const courseId = document.getElementById('schedule-exam-course').value;
    const semester = parseInt(document.getElementById('schedule-exam-sem').value);
    const academicYear = document.getElementById('schedule-exam-year').value;
    const examType = document.getElementById('schedule-exam-type').value;
    const maxMarks = parseFloat(document.getElementById('schedule-exam-max-marks').value);
    const examDate = document.getElementById('schedule-exam-date').value;
    const startTime = document.getElementById('schedule-exam-time').value + ':00';
    const durationMins = parseInt(document.getElementById('schedule-exam-duration').value);
    const venue = document.getElementById('schedule-exam-venue').value;

    try {
        await apiFetch(`/exams/schedule`, {
            method: 'POST',
            body: {
                courseId: parseInt(courseId), semester, academicYear, examType,
                examDate, startTime, durationMins, venue, maxMarks
            }
        });
        showToast('Exam scheduled successfully!');
        document.getElementById('exam-schedule-form').reset();
        loadScheduledExams();
    } catch (err) {}
}

async function publishExamGrades(examId) {
    try {
        await apiFetch(`/exams/${examId}/publish`, { method: 'PUT' });
        showToast('Exam grades published to student cards.');
        loadScheduledExams();
    } catch (err) {}
}

async function loadExamCompilerData() {
    const studentSelect = document.getElementById('compile-student-id');
    const resultsTbody = document.getElementById('results-registry-tbody');

    try {
        // Load students dropdown
        const users = await apiFetch(`/users?role=student`);
        studentSelect.innerHTML = users.data.map(u => `
            <option value="${u.userId}">${u.name} (ID: ${u.userId})</option>
        `).join('');

        // Render all student results
        resultsTbody.innerHTML = ``;
        for (const u of users.data) {
            try {
                const results = await apiFetch(`/exams/student/${u.userId}/results`);
                results.data.forEach(r => {
                    resultsTbody.innerHTML += `
                        <tr>
                            <td>${u.name}</td>
                            <td>Sem ${r.semester} (${r.academicYear})</td>
                            <td>${r.sgpa}</td>
                            <td>${r.cgpa}</td>
                            <td>${r.backlogs}</td>
                        </tr>
                    `;
                });
            } catch (err) {}
        }
    } catch (err) {}
}

async function handleCompileResult(e) {
    e.preventDefault();
    const studentId = document.getElementById('compile-student-id').value;
    const year = document.getElementById('compile-year').value;
    const sem = document.getElementById('compile-semester').value;

    try {
        await apiFetch(`/exams/student/${studentId}/compile-result?academicYear=${year}&semester=${sem}`, {
            method: 'POST'
        });
        showToast('GPA & CGPA Result card compiled successfully.');
        loadExamCompilerData();
    } catch (err) {}
}

// ==========================================
// MODULE: ACCOUNTS PORTAL
// ==========================================
async function loadAccountsLedger() {
    const studentSelect = document.getElementById('invoice-student-id');
    const ledgerTbody = document.getElementById('ledger-invoices-tbody');
    const status = document.getElementById('ledger-filter-status').value;
    document.getElementById('invoice-duedate').value = new Date(Date.now() + 30*24*60*60*1000).toISOString().substring(0,10);

    try {
        // Populate students
        const users = await apiFetch(`/users?role=student`);
        studentSelect.innerHTML = users.data.map(u => `
            <option value="${u.userId}">${u.name} (ID: ${u.userId})</option>
        `).join('');

        // Populate invoices
        const res = await apiFetch(`/fees/invoices?status=${status}`);
        if (res.data.content.length === 0) {
            ledgerTbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted)">No invoices in ${status} status.</td></tr>`;
            return;
        }

        ledgerTbody.innerHTML = res.data.content.map(i => `
            <tr>
                <td>#INV-${i.invoiceId}</td>
                <td>${i.studentName}</td>
                <td>$${i.netPayable}</td>
                <td>${i.dueDate}</td>
                <td><span class="badge ${status === 'PAID' ? 'badge-success' : 'badge-warning'}">${i.status}</span></td>
            </tr>
        `).join('');
    } catch (err) {}
}

async function handleGenerateInvoice(e) {
    e.preventDefault();
    const studentId = document.getElementById('invoice-student-id').value;
    const academicYear = document.getElementById('invoice-year').value;
    const semester = parseInt(document.getElementById('invoice-semester').value);
    const tuitionFee = parseFloat(document.getElementById('invoice-tuition').value);
    const hostelFee = parseFloat(document.getElementById('invoice-hostel').value);
    const labFee = parseFloat(document.getElementById('invoice-lab').value);
    const activityFee = parseFloat(document.getElementById('invoice-activity').value);
    const scholarshipAdjusted = parseFloat(document.getElementById('invoice-scholarship').value);
    const dueDate = document.getElementById('invoice-duedate').value;

    try {
        await apiFetch(`/fees/invoices`, {
            method: 'POST',
            body: {
                studentId: parseInt(studentId), academicYear, semester,
                tuitionFee, hostelFee, libraryFee: 0, labFee, activityFee,
                scholarshipAdjusted, dueDate
            }
        });
        showToast('Invoice generated successfully!');
        loadAccountsLedger();
    } catch (err) {}
}

// ==========================================
// MODULE: ADMIN PORTAL
// ==========================================

// 1. PROGRAMS
async function loadAdminPrograms() {
    const tbody = document.getElementById('admin-programs-tbody');
    try {
        const res = await apiFetch(`/programs`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted)">No programs seeded.</td></tr>`;
            return;
        }
        tbody.innerHTML = res.data.map(p => `
            <tr>
                <td>${p.programId}</td>
                <td>${p.programName}</td>
                <td>${p.level} (${p.durationYears} Years)</td>
                <td>${p.totalSeats}</td>
                <td><span class="badge badge-success">${p.status}</span></td>
            </tr>
        `).join('');
    } catch (err) {}
}

async function handleCreateProgram(e) {
    e.preventDefault();
    const programName = document.getElementById('prog-name').value;
    const departmentId = parseInt(document.getElementById('prog-dept').value);
    const level = document.getElementById('prog-level').value;
    const durationYears = parseInt(document.getElementById('prog-duration').value);
    const totalSeats = parseInt(document.getElementById('prog-seats').value);

    try {
        await apiFetch(`/programs`, {
            method: 'POST',
            body: { programName, departmentId, level, durationYears, totalSeats }
        });
        showToast('Program created successfully!');
        document.getElementById('create-program-form').reset();
        loadAdminPrograms();
    } catch (err) {}
}

// 2. COURSES
async function loadAdminCourses() {
    const programSelect = document.getElementById('course-program-id');
    const facultySelect = document.getElementById('course-faculty-id');
    const tbody = document.getElementById('admin-courses-tbody');

    try {
        // Load Programs Select
        const programs = await apiFetch(`/programs`);
        programSelect.innerHTML = programs.data.map(p => `
            <option value="${p.programId}">${p.programName} (${p.level})</option>
        `).join('');

        // Load Faculty Select
        const faculty = await apiFetch(`/users?role=faculty`);
        facultySelect.innerHTML = faculty.data.map(f => `
            <option value="${f.userId}">${f.name}</option>
        `).join('');

        // Load Course Table
        const courses = await apiFetch(`/courses`);
        if (courses.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted)">No courses created.</td></tr>`;
            return;
        }
        tbody.innerHTML = courses.data.map(c => `
            <tr>
                <td>${c.courseCode}</td>
                <td>${c.courseName}</td>
                <td>${c.programName} (Sem ${c.semester})</td>
                <td>${c.credits} Credits</td>
                <td>${c.facultyName || 'TBA'}</td>
            </tr>
        `).join('');
    } catch (err) {}
}

async function handleCreateCourse(e) {
    e.preventDefault();
    const courseName = document.getElementById('course-title').value;
    const courseCode = document.getElementById('course-code').value;
    const programId = parseInt(document.getElementById('course-program-id').value);
    const semester = parseInt(document.getElementById('course-semester').value);
    const credits = parseInt(document.getElementById('course-credits').value);
    const facultyId = parseInt(document.getElementById('course-faculty-id').value);

    try {
        await apiFetch(`/courses`, {
            method: 'POST',
            body: { courseName, courseCode, programId, semester, credits, facultyId }
        });
        showToast('Course created successfully!');
        document.getElementById('create-course-form').reset();
        loadAdminCourses();
    } catch (err) {}
}

// 3. HOSTELS
async function loadAdminHostels() {
    const studentSelect = document.getElementById('allot-student-id');
    const roomSelect = document.getElementById('allot-room-id');
    const tbody = document.getElementById('admin-hostels-tbody');
    document.getElementById('allot-date').value = new Date().toISOString().substring(0,10);

    try {
        // Load Students dropdown
        const students = await apiFetch(`/users?role=student`);
        studentSelect.innerHTML = students.data.map(s => `
            <option value="${s.userId}">${s.name}</option>
        `).join('');

        // Load Available Rooms dropdown
        const availableRooms = await apiFetch(`/hostel/rooms/available`);
        roomSelect.innerHTML = availableRooms.data.map(r => `
            <option value="${r.roomId}">${r.hostelBlock} Room ${r.roomNumber} (${r.roomType})</option>
        `).join('');

        // Load All Rooms List
        const allRooms = await apiFetch(`/hostel/rooms`);
        tbody.innerHTML = allRooms.data.map(r => `
            <tr>
                <td><strong>${r.hostelBlock} Room ${r.roomNumber}</strong></td>
                <td>${r.roomType}</td>
                <td>${r.occupiedCount} / ${r.capacity} Occupied</td>
                <td><span class="badge ${r.status === 'AVAILABLE' ? 'badge-success' : 'badge-danger'}">${r.status}</span></td>
            </tr>
        `).join('');
    } catch (err) {}
}

async function handleAllotHostel(e) {
    e.preventDefault();
    const studentId = parseInt(document.getElementById('allot-student-id').value);
    const roomId = parseInt(document.getElementById('allot-room-id').value);
    const academicYear = document.getElementById('allot-year').value;
    const checkinDate = document.getElementById('allot-date').value;

    try {
        await apiFetch(`/hostel/allotments`, {
            method: 'POST',
            body: { studentId, roomId, academicYear, checkinDate }
        });
        showToast('Hostel room allotted successfully!');
        loadAdminHostels();
    } catch (err) {}
}

// 4. BOOKINGS
async function loadAdminBookings() {
    const tbody = document.getElementById('admin-bookings-tbody');
    try {
        const res = await apiFetch(`/bookings`);
        if (res.data.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted)">No facility bookings found.</td></tr>`;
            return;
        }

        tbody.innerHTML = res.data.map(b => {
            let badgeClass = 'badge-warning';
            if (b.status === 'APPROVED') badgeClass = 'badge-success';
            if (b.status === 'REJECTED') badgeClass = 'badge-danger';

            let actionHtml = '';
            if (b.status === 'REQUESTED') {
                actionHtml = `
                    <button class="btn btn-success" style="padding: 6px 12px; font-size: 12px;" onclick="updateBookingStatus(${b.bookingId}, 'APPROVED')">Approve</button>
                    <button class="btn btn-danger" style="padding: 6px 12px; font-size: 12px;" onclick="updateBookingStatus(${b.bookingId}, 'REJECTED')">Reject</button>
                `;
            } else {
                actionHtml = `<span style="color: var(--text-muted)">None</span>`;
            }

            return `
                <tr>
                    <td>#B-${b.bookingId}</td>
                    <td>${b.facilityName}</td>
                    <td>${b.bookedByName} (ID: ${b.bookedById})</td>
                    <td>${b.bookingDate} (${b.startTime.substring(0,5)} - ${b.endTime.substring(0,5)})</td>
                    <td>${b.purpose}</td>
                    <td><span class="badge ${badgeClass}">${b.status}</span></td>
                    <td>${actionHtml}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function updateBookingStatus(bookingId, status) {
    try {
        await apiFetch(`/bookings/${bookingId}/status?status=${status}`, { method: 'PUT' });
        showToast(`Booking status set to ${status}.`);
        loadAdminBookings();
    } catch (err) {}
}

// 5. ADMISSIONS
async function loadAdminAdmissions() {
    const tbody = document.getElementById('admin-admissions-tbody');
    const status = document.getElementById('admin-admissions-filter-status').value;

    try {
        const res = await apiFetch(`/admissions?status=${status}`);
        const contentList = res.data.content;
        if (contentList.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted)">No applications in ${status} status.</td></tr>`;
            return;
        }

        tbody.innerHTML = contentList.map(a => {
            let nextActionHtml = '';
            if (a.status === 'SUBMITTED') {
                nextActionHtml = `<button class="btn btn-primary" style="padding: 6px 12px; font-size: 12px;" onclick="updateAdmissionStatus(${a.applicationId}, 'SHORTLISTED')">Shortlist</button>`;
            } else if (a.status === 'SHORTLISTED') {
                nextActionHtml = `<button class="btn btn-primary" style="padding: 6px 12px; font-size: 12px;" onclick="updateAdmissionStatus(${a.applicationId}, 'DOCUMENTS_VERIFIED')">Verify Docs</button>`;
            } else if (a.status === 'DOCUMENTS_VERIFIED') {
                nextActionHtml = `<button class="btn btn-success" style="padding: 6px 12px; font-size: 12px;" onclick="updateAdmissionStatus(${a.applicationId}, 'OFFER_ISSUED')">Issue Offer</button>`;
            } else {
                nextActionHtml = `<span style="color: var(--text-muted)">No action</span>`;
            }

            return `
                <tr>
                    <td>${a.applicationId}</td>
                    <td>${a.applicantName}</td>
                    <td>${a.programName}</td>
                    <td>${a.qualifyingScore}%</td>
                    <td>${a.applicationDate}</td>
                    <td><span class="badge badge-info">${a.status}</span></td>
                    <td>
                        ${nextActionHtml}
                        ${a.status !== 'REJECTED' && a.status !== 'ENROLLED' && a.status !== 'WITHDRAWN' ? 
                        `<button class="btn btn-danger" style="padding: 6px 12px; font-size: 12px; margin-left: 5px;" onclick="updateAdmissionStatus(${a.applicationId}, 'REJECTED')">Reject</button>` : ''}
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function updateAdmissionStatus(appId, status) {
    try {
        await apiFetch(`/admissions/${appId}/status`, {
            method: 'PUT',
            body: { status }
        });
        showToast(`Admission application set to ${status}.`);
        loadAdminAdmissions();
    } catch (err) {}
}

// 6. USERS
async function loadAdminUsers() {
    const tbody = document.getElementById('admin-users-tbody');
    try {
        const res = await apiFetch(`/users`);
        tbody.innerHTML = res.data.map(u => {
            const isSuspended = u.status === 'SUSPENDED';
            return `
                <tr>
                    <td>${u.userId}</td>
                    <td>${u.name}</td>
                    <td>${u.email}</td>
                    <td>${u.phone || 'N/A'}</td>
                    <td><span class="badge badge-info">${u.role}</span></td>
                    <td><span class="badge ${isSuspended ? 'badge-danger' : 'badge-success'}">${u.status}</span></td>
                    <td>
                        ${isSuspended ? 
                        `<button class="btn btn-success" style="padding: 6px 12px; font-size: 12px;" onclick="changeUserStatus(${u.userId}, 'ACTIVE')">Activate</button>` :
                        `<button class="btn btn-danger" style="padding: 6px 12px; font-size: 12px;" onclick="changeUserStatus(${u.userId}, 'SUSPENDED')">Suspend</button>`}
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {}
}

async function changeUserStatus(userId, status) {
    try {
        await apiFetch(`/users/${userId}/status?status=${status}`, { method: 'PUT' });
        showToast(`User status updated to ${status}.`);
        loadAdminUsers();
    } catch (err) {}
}
