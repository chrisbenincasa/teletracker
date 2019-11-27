import React, { Component } from 'react';
import {
  Chip,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { Genre } from '../../types';
import _ from 'lodash';

const styles = (theme: Theme) =>
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
  });

interface OwnProps {
  handleChange: (genres?: number[]) => void;
  genres?: Genre[];
  disabledGenres?: number[];
  selectedGenres: number[];
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

class GenreSelect extends Component<Props> {
  updateSelectedGenres = (param: string, value?: number) => {
    const { selectedGenres } = this.props;
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
      this.props.handleChange(newSelectedGenres);
    }
  };

  render() {
    const { classes, disabledGenres, genres, selectedGenres } = this.props;
    const excludeGenres =
      disabledGenres && selectedGenres
        ? _.difference(disabledGenres, selectedGenres)
        : undefined;

    return (
      <div className={classes.genreContainer}>
        <Typography display="block">Genre</Typography>
        <div className={classes.chipContainer}>
          <Chip
            key={0}
            onClick={() => this.updateSelectedGenres('genres', undefined)}
            size="medium"
            color={
              !selectedGenres || selectedGenres.length === 0
                ? 'secondary'
                : 'primary'
            }
            label="All"
            className={classes.chip}
            disabled={!!excludeGenres}
          />
          {(genres || [])
            .sort((a, b) => (a.name > b.name ? 1 : -1))
            .map(item => {
              return (
                <Chip
                  key={item.id}
                  onClick={() => this.updateSelectedGenres('genres', item.id)}
                  size="medium"
                  color={
                    selectedGenres && selectedGenres.includes(item.id)
                      ? 'secondary'
                      : 'primary'
                  }
                  label={item.name}
                  className={classes.chip}
                  disabled={excludeGenres && excludeGenres.includes(item.id)}
                />
              );
            })}
        </div>
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(GenreSelect));
