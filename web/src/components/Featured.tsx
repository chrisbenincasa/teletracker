import {
  CardMedia,
  createStyles,
  Fade,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Link as RouterLink } from 'react-router-dom';
import React, { Component } from 'react';
import Thing from '../types/Thing';
import AddToListDialog from './AddToListDialog';
import withUser, { WithUserProps } from './withUser';
import { ResponsiveImage } from '../components/ResponsiveImage';
import ManageTracking from '../components/ManageTracking';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';

const styles = (theme: Theme) =>
  createStyles({
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 230,
      },
      width: 115,
      display: 'flex',
      flex: '0 1 auto',
      flexDirection: 'column',
      position: 'absolute',
      top: 20,
      left: 20,
    },
    title: {
      [theme.breakpoints.up('sm')]: {
        fontSize: '3em',
      },
      fontSize: '1em',
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      fontWeight: 700,
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      position: 'absolute',
      bottom: 0,
      right: 10,
      marginBottom: 20,
    },
  });

interface OwnProps {
  featuredItem: Thing;
}

type Props = OwnProps & WithStyles<typeof styles> & WithUserProps;

interface State {
  manageTrackingModalOpen: boolean;
}

class Featured extends Component<Props, State> {
  state: State = {
    manageTrackingModalOpen: false,
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTitle = (thing: Thing) => {
    const { classes } = this.props;
    const title = thing.name || '';

    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h3" className={classes.title}>
          {`${title}`}
        </Typography>
      </div>
    );
  };

  renderFeaturedItem = () => {
    let { classes, featuredItem, userSelf } = this.props;
    const { manageTrackingModalOpen } = this.state;

    return featuredItem ? (
      <Fade in={true}>
        <div style={{ position: 'relative' }}>
          <ResponsiveImage
            item={featuredItem}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              width: '100%',
              height: '100%',
              maxHeight: 424,
            }}
            pictureStyle={{
              display: 'block',
            }}
          />
          <div className={classes.posterContainer}>
            <RouterLink
              to={'/' + featuredItem.type + '/' + featuredItem.normalizedName}
              style={{
                display: 'block',
                height: '100%',
                textDecoration: 'none',
              }}
            >
              <CardMedia
                src={imagePlaceholder}
                item={featuredItem}
                component={ResponsiveImage}
                imageType="poster"
                imageStyle={{
                  width: '100%',
                  boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                }}
                pictureStyle={{
                  display: 'block',
                }}
              />
            </RouterLink>

            <ManageTracking itemDetail={featuredItem} />
            <AddToListDialog
              open={manageTrackingModalOpen}
              onClose={this.closeManageTrackingModal.bind(this)}
              userSelf={userSelf!}
              item={featuredItem}
            />
          </div>
          {this.renderTitle(featuredItem)}
        </div>
      </Fade>
    ) : null;
  };

  render() {
    return this.renderFeaturedItem();
  }
}

export default withUser(withStyles(styles)(Featured));
