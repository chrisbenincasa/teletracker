import * as constants from '../constants';

export const increment = () => {
  return dispatch => {
    dispatch({
      type: constants.INCREMENT_REQUESTED
    })

    dispatch({
      type: constants.INCREMENT
    })
  }
}

export const incrementAsync = () => {
  return dispatch => {
    dispatch({
      type: constants.INCREMENT_REQUESTED
    })

    return setTimeout(() => {
      dispatch({
        type: constants.INCREMENT
      })
    }, 3000)
  }
}

export const decrement = () => {
  return dispatch => {
    dispatch({
      type: constants.DECREMENT_REQUESTED
    })

    dispatch({
      type: constants.DECREMENT
    })
  }
}

export const decrementAsync = () => {
  return dispatch => {
    dispatch({
      type: constants.DECREMENT_REQUESTED
    })

    return setTimeout(() => {
      dispatch({
        type: constants.DECREMENT
      })
    }, 3000)
  }
}