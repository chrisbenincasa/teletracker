import React from 'react';
import { SectionList, Text, TouchableHighlight, View } from 'react-native';
import Search from 'react-native-search-box';
import Swipeout from 'react-native-swipeout';
import Icon from 'react-native-vector-icons/dist/FontAwesome';
import { connect, Dispatch } from 'react-redux';

import UserActions from '../Redux/UserRedux';
import styles from './Styles/ItemListStyle';

// More info here: https://facebook.github.io/react-native/docs/sectionlist.html

// Styles
class ItemList extends React.PureComponent  {

  /* ***********************************************************
  * STEP 1
  * This is an array of objects with the properties you desire
  * Usually this should come from Redux mapStateToProps
  *************************************************************/
  state = {
    data: [
      {
        key: 'Movies',
        data: [
          {title: 'The Fate of the Furious', platform: 'iTunes'},
          {title: 'Patti Cake$', platform: 'HBO'}
        ]
      }, {
        key: 'TV',
        data: [
          {title: 'Halt & Catch Fire', platform: 'Netflix'},
          {title: 'Hamilton\'s Pharmacopeia', platform: 'Vice'}
        ]
      }
    ]
  };

  componentWillMount() {
    this.props.loadUserSelf();
  }


  /* ***********************************************************
  * STEP 3
  * `renderItem` function - How each cell should be rendered
  * It's our best practice to place a single component here:
  *
  * e.g.
  *   return <MyCustomCell title={item.title} platform={item.platform} />
  *
  * For sections with different cells (heterogeneous lists), you can do branch
  * logic here based on section.key OR at the data level, you can provide
  * `renderItem` functions in each section.
  *
  * Note: You can remove section/separator functions and jam them in here
  *************************************************************/
  renderItem ({section, item}) {
    const deleteIcon = <Icon name="trash-o" size={20} color="#fff" />;
    let swipeoutBtns = [
      {
        text: deleteIcon
      }
    ];

    return (
      <Swipeout right={swipeoutBtns} 
        autoClose={true} backgroundColor= 'transparent'>
        <TouchableHighlight
          onPress={() => this.props.navigation.navigate('ItemDetailScreen')} 
        >
        <View style={styles.row} >
          <Text style={styles.boldLabel}>{item.title}</Text>
          <Text style={styles.label}>{item.platform}</Text>
        </View>
      </TouchableHighlight>
      </Swipeout>
    )
  }

    // Important: You must return a Promise
    beforeFocus = () => {
      return new Promise((resolve, reject) => {
        console.log('beforeFocus');
        resolve();
      });
    }

    // Important: You must return a Promise
    onFocus = (text) => {
      return new Promise((resolve, reject) => {
        console.log('onFocus', text);
        resolve();
      });
    }

    // Important: You must return a Promise
    afterFocus = () => {
      return new Promise((resolve, reject) => {
        console.log('afterFocus');
        resolve();
      });
    }

  // Conditional branching for section headers, also see step 3
  renderSectionHeader ({section}) {
    switch (section.key) {
      case 'Movies':
        return <View style={styles.sectionHeader}><Text style={styles.boldLabel}>Movies</Text></View>
      default:
        return <View style={styles.sectionHeader}><Text style={styles.boldLabel}>TV Shows</Text></View>
    }
  }

  /* ***********************************************************
  * STEP 2
  * Consider the configurations we've set below.  Customize them
  * to your liking!  Each with some friendly advice.
  *
  * Removing a function here will make SectionList use default
  *************************************************************/

  // Show this when data is empty
  renderEmpty = () =>
    <Text style={styles.label}> Empty list, yo! </Text>;


  // The default function if no Key is provided is index
  // an identifiable key is important if you plan on
  // item reordering.  Otherwise index is fine
  keyExtractor = (item, index) => index;

  // How many items should be kept im memory as we scroll?
  oneScreensWorth = 20;

  // extraData is for anything that is not indicated in data
  // for instance, if you kept "favorites" in `this.state.favs`
  // pass that in, so changes in favorites will cause a re-render
  // and your renderItem will have access to change depending on state
  // e.g. `extraData`={this.state.favs}

  // Optimize your list if the height of each item can be calculated
  // by supplying a constant height, there is no need to measure each
  // item after it renders.  This can save significant time for lists
  // of a size 100+
  // e.g. itemLayout={(data, index) => (
  //   {length: ITEM_HEIGHT, offset: ITEM_HEIGHT * index, index}
  // )}

  render () {
    return (
      <View style={styles.container}>
       <Search
          ref="search_box"
          /**
          * There are many props options:
          * https://github.com/agiletechvn/react-native-search-box
          */
        />
        <Text style={{color: 'white'}}>{this.props.user && this.props.user.name ? this.props.user.name : 'Fetching...'}</Text>
        <SectionList
          renderSectionHeader={this.renderSectionHeader}
          sections={this.state.data}
          contentContainerStyle={styles.listContent}
          data={this.state.dataObjects}
          renderItem={this.renderItem.bind(this)}
          keyExtractor={this.keyExtractor}
          initialNumToRender={this.oneScreensWorth}
          ListHeaderComponent={this.renderHeader}
          ListEmptyComponent={this.renderEmpty}
        />
      </View>
    )
  }
};

const mapStateToProps = (state) => {
  return {
    user: state.user
  }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    loadUserSelf: () => {
      dispatch(UserActions.userSelfRequest());
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemList);
