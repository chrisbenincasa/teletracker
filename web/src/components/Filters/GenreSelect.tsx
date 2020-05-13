import React, { useContext } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import _ from 'lodash';
import { useGenres } from '../../hooks/useStateMetadata';
import { FilterContext } from './FilterContext';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    genreContainer: {
      display: 'flex',
      flexDirection: 'column',
    },
  }),
);

interface NewProps {
  readonly handleChange: (genres?: number[]) => void;
  readonly showTitle?: boolean;
  readonly disabledGenres?: number[];
}

export default function GenreSelect(props: NewProps) {
  const classes = useStyles();
  const genres = useGenres();
  const {
    filters: { genresFilter },
  } = useContext(FilterContext);
  const selectedGenres = genresFilter || [];

  const { disabledGenres } = props;
  const excludeGenres =
    disabledGenres && selectedGenres
      ? _.difference(disabledGenres, selectedGenres)
      : undefined;

  const updateSelectedGenres = (param: string, value?: number) => {
    let newSelectedGenres: number[];

    if (value) {
      // If genre isn't filtered yet, add it to current filter
      if (!selectedGenres.includes(value)) {
        newSelectedGenres = [...selectedGenres, value];
      } else {
        newSelectedGenres = selectedGenres.filter(genreId => genreId !== value);
      }
    } else {
      // User selected 'All', reset genre filter
      newSelectedGenres = [];
    }

    if (_.xor(selectedGenres, newSelectedGenres).length !== 0) {
      props.handleChange(newSelectedGenres);
    }
  };

  const chips = _.chain(genres || [])
    .sortBy('name')
    .map(item => {
      return (
        <Chip
          key={item.id}
          onClick={() => updateSelectedGenres('genres', item.id)}
          size="medium"
          color={
            selectedGenres && selectedGenres.includes(item.id)
              ? 'primary'
              : 'secondary'
          }
          label={item.name}
          className={classes.chip}
          disabled={excludeGenres && excludeGenres.includes(item.id)}
        />
      );
    })
    .value();
  return (
    <div className={classes.genreContainer}>
      {props.showTitle && <Typography display="block">Genre</Typography>}
      <div className={classes.chipContainer}>
        <Chip
          key={0}
          onClick={() => updateSelectedGenres('genres', undefined)}
          size="medium"
          color={
            !selectedGenres || selectedGenres.length === 0
              ? 'primary'
              : 'secondary'
          }
          label="All"
          className={classes.chip}
          disabled={!!excludeGenres}
        />
        {chips}
      </div>
    </div>
  );
}

GenreSelect.defaultProps = {
  showTitle: true,
};
