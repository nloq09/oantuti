/** Ignore responses from an older filter/page selection, including older errors. */
export function historyLoader(request, loaded, failed, busy) {
  let generation = 0;
  return async (filter, page) => {
    const current = ++generation; busy(true);
    try { const result = await request(filter, page, 20); if (current === generation) loaded(result); }
    catch (error) { if (current === generation) failed(error); }
    finally { if (current === generation) busy(false); }
  };
}
