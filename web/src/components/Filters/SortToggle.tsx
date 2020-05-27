import React, { useContext } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  MenuItem,
  Select,
  Theme,
  Typography,
} from '@material-ui/core';
import { SortOptions } from '../../types';
import _ from 'lodash';
import { FilterContext } from './FilterContext';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
    chip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
  }),
);

export const sortOptionToName: { [K in SortOptions]?: string } = {
  popularity: 'Popularity',
  added_time: 'Date Added',
  recent: 'Release Date',
  'rating|imdb': 'IMDb Rating',
};

const defaultSortOptions: SortOptions[] = [
  'popularity',
  'added_time',
  'recent',
  'rating|imdb',
];

interface Props {
  readonly showTitle?: boolean;
  readonly isListDynamic?: boolean;
  readonly validSortOptions?: SortOptions[];
  readonly handleChange: (sortOrder: SortOptions) => void;
}

export default function SortToggle(props: Props) {
  const classes = useStyles();
  const { filters } = useContext(FilterContext);

  const updateSort = (param, sort) => {
    const isNewSortDefault = _.isUndefined(sort);
    const isPrevSortDefault = _.isUndefined(filters.sortOrder);
    const newSort = isNewSortDefault ? undefined : sort;

    if (newSort !== filters.sortOrder) {
      props.handleChange(newSort);
    }
  };

  const sorts = props.validSortOptions || defaultSortOptions;
  const menuItems = sorts.map(sort => (
    <Chip
      key={sort}
      onClick={() => updateSort('sort', sort)}
      size="medium"
      color={filters.sortOrder === sort ? 'primary' : 'secondary'}
      label={sortOptionToName[sort]!}
      className={classes.chip}
    />
  ));

  return (
    <div>
      {props.showTitle && (
        <Typography className={classes.filterLabel} display="block">
          Sort
        </Typography>
      )}
      <div className={classes.chipContainer}>{menuItems}</div>
    </div>
  );
}

SortToggle.defaultProps = {
  showTitle: true,
};
