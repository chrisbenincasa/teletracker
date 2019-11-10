import {
  Chip,
  createStyles,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import _ from 'lodash';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { Genre } from '../../types';
import React, { Component } from 'react';
import {
  parseFilterParamsFromQs,
  updateURLParameters,
} from '../../utils/urlHelper';

const styles = () =>
  createStyles({
    chip: {
      margin: 4,
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
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  genresFilter?: number[];
}

export const getGenreFromUrlParam = () => {
  return parseFilterParamsFromQs(window.location.search).genresFilter;
};

class GenreSelect extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      genresFilter: getGenreFromUrlParam(),
    };
  }

  componentDidUpdate = oldProps => {
    if (oldProps.location.search !== this.props.location.search) {
      // To do, only update this when genre params changed
      this.setState({
        genresFilter: getGenreFromUrlParam(),
      });
    }
  };

  updateURLParam = (param: string, value?: number) => {
    const { genresFilter } = this.state;
    let newValue;

    if (
      value &&
      genresFilter &&
      genresFilter.length > 0 &&
      !genresFilter.includes(value)
    ) {
      // If genre isn't filtered yet, add it to current filter
      newValue = [...genresFilter, value];
    } else if (
      value &&
      genresFilter &&
      genresFilter.length > 0 &&
      genresFilter.includes(value)
    ) {
      // If genre is already filtered, remove it
      newValue = genresFilter.filter(genreId => genreId !== value);
    } else {
      // User selected 'All', reset genre filter
      newValue = value ? [value] : [];
    }

    updateURLParameters(this.props, param, newValue);

    this.setState(
      {
        genresFilter: newValue,
      },
      () => {
        this.props.handleChange(newValue);
      },
    );
  };

  render() {
    const { classes, disabledGenres, genres } = this.props;
    const { genresFilter } = this.state;
    const excludeGenres =
      disabledGenres && genresFilter
        ? _.difference(disabledGenres, genresFilter)
        : undefined;

    return (
      <div className={classes.genreContainer}>
        <Typography display="block">Genre</Typography>
        <div className={classes.chipContainer}>
          <Chip
            key={0}
            onClick={() => this.updateURLParam('genres', undefined)}
            size="medium"
            color={
              !genresFilter || genresFilter.length === 0
                ? 'secondary'
                : 'primary'
            }
            label="All"
            className={classes.chip}
            disabled={!!excludeGenres}
          />
          {(genres || []).map(item => {
            return (
              <Chip
                key={item.id}
                onClick={() => this.updateURLParam('genres', item.id)}
                size="medium"
                color={
                  genresFilter && genresFilter.includes(item.id)
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
