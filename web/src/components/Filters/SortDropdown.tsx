import React, { useContext } from 'react';
import {
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

export default function SortDropDown(props: Props) {
  const classes = useStyles();
  const { filters } = useContext(FilterContext);

  const updateSort = (param, event) => {
    const isNewSortDefault = _.isUndefined(event.target.value);
    const isPrevSortDefault = _.isUndefined(filters.sortOrder);
    const newSort = isNewSortDefault ? undefined : event.target.value;

    if (event.target.value !== filters.sortOrder) {
      console.log(event.target.value);
      props.handleChange(newSort);
    }
  };

  const sorts = props.validSortOptions || defaultSortOptions;
  const menuItems = sorts.map(sort => (
    <MenuItem key={sort} value={sort}>
      {sortOptionToName[sort]!}
    </MenuItem>
  ));

  return (
    <div>
      {props.showTitle && (
        <Typography className={classes.filterLabel} display="block">
          Sort
        </Typography>
      )}
      <Select
        value={
          filters.sortOrder ||
          (props.isListDynamic ? 'popularity' : 'added_time')
        }
        inputProps={{
          name: 'sortOrder',
          id: 'sort-order',
        }}
        onChange={event => updateSort('sort', event)}
      >
        {menuItems}
      </Select>
    </div>
  );
}

SortDropDown.defaultProps = {
  showTitle: true,
};
