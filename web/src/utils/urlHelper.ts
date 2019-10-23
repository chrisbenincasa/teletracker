/*
    Updates or adds URL parameters
*/
export const updateURLParameters = (props, param: string, value?) => {
  const { location } = props;
  let params = new URLSearchParams(location.search);
  let paramExists = params.get(param);
  let cleanValues =
    param === 'sort'
      ? value
      : value && value.length
      ? value.join(',')
      : undefined;

  // User is clicking button more than once, exit
  if (paramExists && value && paramExists === cleanValues) {
    return;
  }

  if (!cleanValues) {
    params.delete(param);
  } else if (paramExists) {
    params.set(param, cleanValues);
  } else {
    params.append(param, cleanValues);
  }
  params.sort();

  props.history.replace(`?${params}`);
};
