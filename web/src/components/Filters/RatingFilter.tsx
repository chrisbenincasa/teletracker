import React, { useContext } from 'react';
import { makeStyles, Slider, Theme, Typography } from '@material-ui/core';
import { SliderChange } from '../../utils/searchFilters';
import { useDebouncedCallback } from 'use-debounce';
import { FilterContext } from './FilterContext';

const styles = makeStyles((theme: Theme) => ({
  sliderContainer: {
    width: '100%',
    padding: theme.spacing(0, 2),
  },
}));

const MIN_RATING = 0;
const MAX_RATING = 10;
const RATING_STEP = 0.5;

interface Props {
  readonly handleChange: (change: SliderChange) => void;
  readonly showTitle?: boolean;
}

const ensureNumberInRange = (num: number, lo: number, hi: number) => {
  return Math.max(Math.min(num, hi), lo);
};

export default function RatingFilter(props: Props) {
  const classes = styles();
  const {
    filters: { sliders },
  } = useContext(FilterContext);

  const [imdbRatingValue, setImdbRatingValue] = React.useState([
    ensureNumberInRange(
      sliders?.imdbRating?.min || MIN_RATING,
      MIN_RATING,
      MAX_RATING,
    ),
    ensureNumberInRange(
      sliders?.imdbRating?.max || MAX_RATING,
      MIN_RATING,
      MAX_RATING,
    ),
  ]);

  const [debouncePropUpdate] = useDebouncedCallback(
    (sliderChange: SliderChange) => {
      if (props.handleChange) {
        props.handleChange(sliderChange);
      }
    },
    250,
  );

  const extractValues = (
    newValue: number[],
    minValue: number,
    maxValue: number,
  ): [number?, number?] => {
    let [min, max] = newValue;
    return [
      min === minValue ? undefined : min,
      max === maxValue ? undefined : max,
    ];
  };

  const handleImdbChange = (event, newValue) => {
    setImdbRatingValue(newValue);
  };

  const handleImdbCommitted = (event, newValue) => {
    let [min, max] = extractValues(newValue, MIN_RATING, MAX_RATING);
    debouncePropUpdate({
      imdbRating: {
        min,
        max,
      },
    });
  };

  return (
    <div className={classes.sliderContainer}>
      {props.showTitle && <Typography>IMDb Rating</Typography>}
      <Slider
        value={imdbRatingValue}
        min={MIN_RATING}
        max={MAX_RATING}
        step={RATING_STEP}
        marks={[
          { value: MIN_RATING, label: MIN_RATING },
          { value: MAX_RATING, label: MAX_RATING },
        ]}
        onChange={handleImdbChange}
        onChangeCommitted={handleImdbCommitted}
        valueLabelDisplay="auto"
        aria-labelledby="range-slider"
      />
    </div>
  );
}

RatingFilter.defaultProps = {
  showTitle: true,
};
