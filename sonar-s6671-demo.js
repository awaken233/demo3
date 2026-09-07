// Sonar rule S6671: Promise rejection reasons should be Error objects.

async function nonCompliantExample() {
  return Promise.reject('User query failed');
}

async function compliantExample() {
  return Promise.reject(new Error('User query failed'));
}

async function observe(label, fn) {
  try {
    await fn();
  } catch (reason) {
    console.log(`\n[${label}]`);
    console.log('type:', typeof reason);
    console.log('instanceof Error:', reason instanceof Error);
    console.log('message:', reason?.message);
    console.log('stack:', reason?.stack);
  }
}

(async () => {
  await observe('non-compliant: reject string', nonCompliantExample);
  await observe('compliant: reject Error', compliantExample);
})();
