import {
  createStyles,
  MenuItem,
  Select,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { SortOptions } from '../../types';
import React, { Component } from 'react';
import {
  parseFilterParamsFromQs,
  updateURLParameters,
} from '../../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
    filterLabel: {
      paddingBottom: theme.spacing() / 2,
    },
  });

interface OwnProps {
  handleChange: (sortOrder: SortOptions) => void;
  isListDynamic?: boolean;
  validSortOptions?: SortOptions[];
  selectedSort: SortOptions;
}

interface RouteParams {
  id: string;
}

const sortOptionToName: { [K in SortOptions]?: string } = {
  popularity: 'Popularity',
  added_time: 'Date Added',
  recent: 'Release Date',
};

const defaultSortOptions = ['popularity', 'added_time', 'recent'];

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

class SortDropDown extends Component<Props> {
  isDefaultSort = newSort => {
    const { isListDynamic } = this.props;

    return (
      (isListDynamic && newSort === 'popularity') ||
      (!isListDynamic && newSort === 'added_time') ||
      newSort === 'default'
    );
  };

  updateURLParam = (param, event) => {
    const isNewSortDefault = this.isDefaultSort(event.target.value);
    const isPrevSortDefault = this.isDefaultSort(this.props.selectedSort);
    const newSort = isNewSortDefault ? undefined : event.target.value;

    if (
      event.target.value !== this.props.selectedSort &&
      !(isNewSortDefault && isPrevSortDefault)
    ) {
      this.props.handleChange(newSort);
    }
  };

  render() {
    const {
      isListDynamic,
      classes,
      selectedSort,
      validSortOptions,
    } = this.props;

    let sorts = validSortOptions || defaultSortOptions;
    let menuItems = sorts.map(sort => (
      <MenuItem value={sort}>{sortOptionToName[sort]!}</MenuItem>
    ));

    return (
      <div>
        <Typography className={classes.filterLabel} display="block">
          Sort
        </Typography>
        <Select
          value={
            selectedSort && !this.isDefaultSort(selectedSort)
              ? selectedSort
              : isListDynamic
              ? 'popularity'
              : 'added_time'
          }
          inputProps={{
            name: 'sortOrder',
            id: 'sort-order',
          }}
          onChange={event => this.updateURLParam('sort', event)}
        >
          {menuItems}
        </Select>
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(SortDropDown));
