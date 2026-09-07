const assert = require('node:assert/strict');
const {
  nonCompliantExample,
  compliantExample,
} = require('./sonar-s6671-demo');

async function captureRejection(fn) {
  try {
    await fn();
    assert.fail('Expected promise to reject');
  } catch (reason) {
    return reason;
  }
}

(async () => {
  const bad = await captureRejection(nonCompliantExample);
  const good = await captureRejection(compliantExample);

  console.log('=== Non-compliant ===');
  console.log('type:', typeof bad);
  console.log('instanceof Error:', bad instanceof Error);
  console.log('message:', bad?.message);
  console.log('has stack:', Boolean(bad?.stack));

  console.log('\n=== Compliant ===');
  console.log('type:', typeof good);
  console.log('instanceof Error:', good instanceof Error);
  console.log('message:', good?.message);
  console.log('has stack:', Boolean(good?.stack));
  console.log('stack first line:', good.stack.split('\n')[0]);

  assert.equal(typeof bad, 'string');
  assert.equal(bad instanceof Error, false);
  assert.equal(bad?.message, undefined);
  assert.equal(bad?.stack, undefined);

  assert.equal(good instanceof Error, true);
  assert.equal(good.message, 'User query failed');
  assert.equal(typeof good.stack, 'string');
  assert.match(good.stack, /^Error: User query failed/);

  console.log('\nVerification passed: Error rejection preserves structured error data and stack trace.');
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
