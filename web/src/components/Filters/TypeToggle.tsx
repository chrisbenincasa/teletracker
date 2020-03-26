import React, { Component } from 'react';
import {
  Chip,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';
import { ItemType } from '../../types';
import { parseFilterParamsFromQs } from '../../utils/urlHelper';

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
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
    typeContainer: {
      display: 'flex',
      flexDirection: 'column',
    },
  });

interface OwnProps {
  handleChange: (type?: ItemType[]) => void;
  selectedTypes?: ItemType[];
  showTitle?: boolean;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps & WithStyles<typeof styles> & WithRouterProps;

export const getTypeFromUrlParam = () => {
  return parseFilterParamsFromQs(window.location.search).itemTypes;
};

class TypeToggle extends Component<Props> {
  static defaultProps = {
    showTitle: true,
  };

  componentDidUpdate = (oldProps: Props) => {
    // TODO: Don't do object equiv
    if (oldProps.router.query !== this.props.router.query) {
      this.setState({
        type: getTypeFromUrlParam(),
      });
    }
  };

  updateTypes = (param: string, value?: ItemType[]) => {
    this.props.handleChange(value);
  };

  render() {
    const { classes, selectedTypes } = this.props;
    const isTypeAll = !selectedTypes || !selectedTypes.length;
    const isTypeMovie = selectedTypes && selectedTypes.includes('movie');
    const isTypeShow = selectedTypes && selectedTypes.includes('show');

    return (
      <div className={classes.typeContainer}>
        {this.props.showTitle && (
          <Typography className={classes.filterLabel} display="block">
            Type
          </Typography>
        )}
        <div className={classes.chipContainer}>
          <Chip
            key={'all'}
            onClick={() => this.updateTypes('type', undefined)}
            size="medium"
            color={isTypeAll ? 'primary' : 'secondary'}
            label="All"
            className={classes.chip}
          />
          <Chip
            key={'Movies'}
            onClick={() => this.updateTypes('type', ['movie'])}
            size="medium"
            color={isTypeMovie ? 'primary' : 'secondary'}
            label="Movies"
            className={classes.chip}
          />
          <Chip
            key={'TV'}
            onClick={() => this.updateTypes('type', ['show'])}
            size="medium"
            color={isTypeShow ? 'primary' : 'secondary'}
            label="TV"
            className={classes.chip}
          />
        </div>
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(TypeToggle));
