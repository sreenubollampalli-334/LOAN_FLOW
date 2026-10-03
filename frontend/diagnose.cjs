const { chromium } = require('@playwright/test');

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  
  const errors = [];
  const networkErrors = [];

  page.on('pageerror', err => {
    errors.push(`Page Error: ${err.message}`);
  });
  
  page.on('console', msg => {
    if (msg.type() === 'error') {
      errors.push(`Console Error: ${msg.text()}`);
    } else {
      console.log(`Console: ${msg.text()}`);
    }
  });

  page.on('requestfailed', request => {
    networkErrors.push(`Failed Request: ${request.url()} - ${request.failure().errorText}`);
  });

  page.on('response', response => {
    if (!response.ok()) {
      networkErrors.push(`Bad Response: ${response.url()} - ${response.status()}`);
    }
  });

  await page.goto('http://localhost:5173/');
  
  // Set local storage
  await page.evaluate(() => {
    localStorage.setItem('token', 'fake-token');
    localStorage.setItem('user', JSON.stringify({ firstName: "Test", roles: ["ROLE_CUSTOMER"] }));
  });
  
  // Reload page to simulate user with local storage
  await page.reload({ waitUntil: 'networkidle' });

  const rootHtml = await page.$eval('#root', el => el.innerHTML).catch(e => 'No #root element found');
  
  console.log("--- DIAGNOSTIC REPORT ---");
  console.log("Root Element HTML:", rootHtml);
  
  console.log("\n--- ERRORS ---");
  errors.forEach(e => console.log(e));
  
  console.log("\n--- NETWORK ERRORS ---");
  networkErrors.forEach(e => console.log(e));

  await browser.close();
})();
