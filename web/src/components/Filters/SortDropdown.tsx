import React, { Component } from 'react';
import {
  createStyles,
  MenuItem,
  Select,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';
import { SortOptions } from '../../types';
import _ from 'lodash';

const styles = (theme: Theme) =>
  createStyles({
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
  });

interface OwnProps {
  handleChange: (sortOrder: SortOptions) => void;
  isListDynamic?: boolean;
  validSortOptions?: SortOptions[];
  selectedSort?: SortOptions;
  showTitle?: boolean;
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

type Props = OwnProps & WithStyles<typeof styles> & WithRouterProps;

class SortDropDown extends Component<Props> {
  static defaultProps = {
    showTitle: true,
  };

  isDefaultSort = newSort => {
    const { isListDynamic } = this.props;

    return (
      (isListDynamic && newSort === 'popularity') ||
      (!isListDynamic && newSort === 'added_time') ||
      newSort === 'default'
    );
  };

  updateURLParam = (param, event) => {
    const isNewSortDefault = _.isUndefined(event.target.value);
    const isPrevSortDefault = _.isUndefined(this.props.selectedSort);
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
      <MenuItem key={sort} value={sort}>
        {sortOptionToName[sort]!}
      </MenuItem>
    ));

    return (
      <div>
        {this.props.showTitle && (
          <Typography className={classes.filterLabel} display="block">
            Sort
          </Typography>
        )}
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
